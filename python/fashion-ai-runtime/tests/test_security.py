import asyncio
import base64
import hashlib
import json
import logging
import time
from collections.abc import AsyncIterator
from pathlib import Path
from typing import TypedDict, cast

import httpx
import pytest
from fastapi.testclient import TestClient
from support import (
    TEST_PREVIOUS_KEY_ID,
    TEST_PREVIOUS_SECRET,
    runtime_settings,
    signed_headers,
)

from fashion_ai.api.security import (
    HmacServiceAuthenticator,
    HmacServiceRequestSigner,
    InMemoryNonceStore,
    ServiceSigningUnavailableError,
    build_canonical_request,
    sign_canonical_request,
)
from fashion_ai.main import create_app
from fashion_ai.ports.service_auth import ServiceRequestToSign
from fashion_ai.settings import HmacKey, RuntimeSettings
from fashion_ai.telemetry import StructuredJsonFormatter
from fashion_ai.telemetry.runtime import route_template_label

INTERNAL_PATH = "/internal/v1/capabilities"
VECTORS_PATH = (
    Path(__file__).resolve().parents[3]
    / "contracts"
    / "fashion"
    / "examples"
    / "v1"
    / "service-authentication-vectors.json"
)


class ServiceAuthenticationVector(TypedDict):
    name: str
    secret_base64: str
    method: str
    raw_path: str
    service_id: str
    key_id: str
    audience: str
    timestamp: str
    nonce: str
    body_utf8: str
    content_sha256: str
    canonical_utf8: str
    signature: str


def _load_authentication_vectors() -> list[ServiceAuthenticationVector]:
    document = cast(
        dict[str, object],
        json.loads(VECTORS_PATH.read_text(encoding="utf-8")),
    )
    return cast(list[ServiceAuthenticationVector], document["vectors"])


AUTHENTICATION_VECTORS = _load_authentication_vectors()


@pytest.mark.parametrize(
    "vector",
    AUTHENTICATION_VECTORS,
    ids=[vector["name"] for vector in AUTHENTICATION_VECTORS],
)
def test_hmac_v1_matches_shared_cross_language_fixed_vectors(
    vector: ServiceAuthenticationVector,
) -> None:
    body = vector["body_utf8"].encode()
    digest = hashlib.sha256(body).hexdigest()
    canonical = build_canonical_request(
        method=vector["method"],
        raw_path=vector["raw_path"],
        service_id=vector["service_id"],
        audience=vector["audience"],
        timestamp=vector["timestamp"],
        nonce=vector["nonce"],
        content_sha256=digest,
    )

    assert digest == vector["content_sha256"]
    assert canonical.decode() == vector["canonical_utf8"]
    secret = base64.b64decode(vector["secret_base64"], validate=True)
    assert sign_canonical_request(secret, canonical) == vector["signature"]


def test_outbound_signer_consumes_active_key_and_shared_gateway_vector() -> None:
    vector = next(
        item
        for item in AUTHENTICATION_VECTORS
        if item["name"] == "python-to-java-agent-runs-execute"
    )
    secret = base64.b64decode(vector["secret_base64"], validate=True)
    signer = HmacServiceRequestSigner(
        active_key=HmacKey(key_id=vector["key_id"], secret=secret),
        clock=lambda: float(vector["timestamp"]),
        nonce_factory=lambda: vector["nonce"],
    )

    headers = signer.sign(
        ServiceRequestToSign(
            method=vector["method"],
            raw_path=vector["raw_path"],
            query_string=b"",
            body=vector["body_utf8"].encode(),
            request_id="00000000-0000-4000-8000-000000000002",
            correlation_id="corr:run-001",
            traceparent=("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
        )
    )

    assert signer.configured is True
    assert headers["X-Fashion-Service-Id"] == "fashion-ai-runtime"
    assert headers["X-Fashion-Audience"] == "ruoyi-fashion"
    assert headers["X-Fashion-Key-Id"] == vector["key_id"]
    assert headers["X-Fashion-Content-SHA256"] == vector["content_sha256"]
    assert headers["X-Fashion-Signature"] == vector["signature"]


def test_outbound_signer_fails_closed_without_active_key() -> None:
    signer = HmacServiceRequestSigner(active_key=None)
    request = ServiceRequestToSign(
        method="POST",
        raw_path="/internal/v1/agent-runs:execute",
        query_string=b"",
        body=b"{}",
        request_id="00000000-0000-4000-8000-000000000002",
        correlation_id="corr:run-001",
        traceparent=("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
    )

    assert signer.configured is False
    with pytest.raises(ServiceSigningUnavailableError):
        signer.sign(request)


def test_health_is_public_but_unconfigured_internal_api_fails_closed() -> None:
    with TestClient(create_app(settings=RuntimeSettings())) as client:
        assert client.get("/health").status_code == 200
        response = client.get(INTERNAL_PATH)

    assert response.status_code == 401
    assert response.json()["error"] == {
        "code": "SERVICE_AUTHENTICATION_FAILED",
        "message": "服务认证失败",
        "retryable": False,
    }


def test_configured_internal_api_requires_all_authentication_headers() -> None:
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH)

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == (
        'FashionHmac realm="fashion-ai-runtime"'
    )
    assert response.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


@pytest.mark.parametrize(
    "missing_header",
    ["X-Request-Id", "X-Correlation-Id", "traceparent"],
)
def test_required_context_header_is_part_of_service_authentication(
    missing_header: str,
) -> None:
    headers = signed_headers(method="GET", path=INTERNAL_PATH)
    del headers[missing_header]

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


@pytest.mark.parametrize(
    ("header_name", "invalid_value"),
    [
        ("X-Request-Id", "not-a-uuid"),
        ("X-Correlation-Id", "contains whitespace"),
        (
            "traceparent",
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
        ),
        (
            "traceparent",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
        ),
    ],
)
def test_invalid_context_header_is_rejected_before_business_processing(
    header_name: str,
    invalid_value: str,
) -> None:
    headers = signed_headers(method="GET", path=INTERNAL_PATH)
    headers[header_name] = invalid_value

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


def test_valid_optional_tracestate_is_authenticated_and_propagated() -> None:
    headers = signed_headers(method="GET", path=INTERNAL_PATH)
    headers["tracestate"] = "vendor=value,tenant@vendor=opaque-1"

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 200
    assert response.headers["tracestate"] == headers["tracestate"]


@pytest.mark.parametrize(
    "tracestate",
    [
        "Vendor=value",
        "vendor=bad=value",
        "vendor=one,vendor=two",
        "vendor=" + ("x" * 513),
    ],
)
def test_invalid_optional_tracestate_is_rejected(tracestate: str) -> None:
    headers = signed_headers(method="GET", path=INTERNAL_PATH)
    headers["tracestate"] = tracestate

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


@pytest.mark.parametrize(
    ("header_name", "replacement"),
    [
        ("X-Fashion-Content-SHA256", "0" * 64),
        ("X-Fashion-Signature", "v1=" + "A" * 43),
        ("X-Fashion-Timestamp", "0"),
    ],
)
def test_invalid_digest_signature_or_timestamp_is_unauthorized(
    header_name: str,
    replacement: str,
) -> None:
    headers = signed_headers(method="GET", path=INTERNAL_PATH)
    headers[header_name] = replacement

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


@pytest.mark.parametrize("offset_seconds", [-301, 301])
def test_correctly_signed_timestamp_outside_clock_window_is_unauthorized(
    offset_seconds: int,
) -> None:
    now = int(time.time())
    settings = runtime_settings()
    authenticator = HmacServiceAuthenticator(
        settings=settings.service_auth,
        nonce_store=InMemoryNonceStore(max_entries=1_000),
        clock=lambda: float(now),
    )
    headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        timestamp=now + offset_seconds,
    )

    with TestClient(
        create_app(settings=settings, authenticator=authenticator)
    ) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


def test_twelve_digit_timestamp_is_accepted_when_clock_matches() -> None:
    timestamp = 100_000_000_000
    settings = runtime_settings()
    authenticator = HmacServiceAuthenticator(
        settings=settings.service_auth,
        nonce_store=InMemoryNonceStore(max_entries=1_000),
        clock=lambda: float(timestamp),
    )
    headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        timestamp=timestamp,
    )

    with TestClient(
        create_app(settings=settings, authenticator=authenticator)
    ) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 200


@pytest.mark.parametrize(
    ("nonce", "expected_status"),
    [
        ("n" * 16, 200),
        ("n" * 128, 200),
        ("n" * 15, 401),
        ("nonce~not~allowed~0001", 401),
    ],
)
def test_nonce_uses_shared_length_and_character_boundaries(
    nonce: str,
    expected_status: int,
) -> None:
    headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        nonce=nonce,
    )

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == expected_status


@pytest.mark.parametrize(
    ("service_id", "audience"),
    [
        ("unknown-java-service", "fashion-ai-runtime"),
        ("ruoyi-fashion", "another-runtime"),
    ],
)
def test_correctly_signed_but_unallowed_identity_or_audience_is_forbidden(
    service_id: str,
    audience: str,
) -> None:
    headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        service_id=service_id,
        audience=audience,
    )

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "SERVICE_NOT_AUTHORIZED"


def test_previous_key_is_accepted_during_rotation_window() -> None:
    headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        key_id=TEST_PREVIOUS_KEY_ID,
        secret=TEST_PREVIOUS_SECRET,
    )

    with TestClient(
        create_app(settings=runtime_settings(include_previous_key=True))
    ) as client:
        response = client.get(INTERNAL_PATH, headers=headers)

    assert response.status_code == 200


def test_nonce_is_global_per_service_across_active_and_previous_keys() -> None:
    nonce = "rotation:nonce:00000001"
    timestamp = int(time.time())
    active_headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        nonce=nonce,
        timestamp=timestamp,
    )
    previous_headers = signed_headers(
        method="GET",
        path=INTERNAL_PATH,
        nonce=nonce,
        timestamp=timestamp,
        key_id=TEST_PREVIOUS_KEY_ID,
        secret=TEST_PREVIOUS_SECRET,
    )

    with TestClient(
        create_app(settings=runtime_settings(include_previous_key=True))
    ) as client:
        first = client.get(INTERNAL_PATH, headers=active_headers)
        replay = client.get(INTERNAL_PATH, headers=previous_headers)

    assert first.status_code == 200
    assert replay.status_code == 401
    assert replay.json()["error"]["code"] == "SERVICE_AUTHENTICATION_FAILED"


def test_query_is_forbidden_by_hmac_v1() -> None:
    headers = signed_headers(method="GET", path=INTERNAL_PATH)

    with TestClient(create_app(settings=runtime_settings())) as client:
        response = client.get(f"{INTERNAL_PATH}?probe=1", headers=headers)

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "SERVICE_NOT_AUTHORIZED"


def test_bounded_nonce_store_fails_closed_at_capacity() -> None:
    settings = runtime_settings()
    authenticator = HmacServiceAuthenticator(
        settings=settings.service_auth,
        nonce_store=InMemoryNonceStore(max_entries=1),
    )
    first_headers = signed_headers(method="GET", path=INTERNAL_PATH)
    second_headers = signed_headers(method="GET", path=INTERNAL_PATH)

    with TestClient(
        create_app(settings=settings, authenticator=authenticator)
    ) as client:
        first = client.get(INTERNAL_PATH, headers=first_headers)
        overloaded = client.get(INTERNAL_PATH, headers=second_headers)

    assert first.status_code == 200
    assert overloaded.status_code == 429
    assert overloaded.json()["error"] == {
        "code": "RATE_LIMITED",
        "message": "服务认证暂时过载",
        "retryable": True,
    }


def test_nonce_store_reclaims_expired_heap_entries() -> None:
    async def exercise_store() -> tuple[str, str, str]:
        store = InMemoryNonceStore(max_entries=1)
        first = await store.claim(
            service_id="ruoyi-fashion",
            nonce="nonce:0000000001",
            now_epoch_seconds=1_000,
            ttl_seconds=600,
        )
        full = await store.claim(
            service_id="ruoyi-fashion",
            nonce="nonce:0000000002",
            now_epoch_seconds=1_001,
            ttl_seconds=600,
        )
        reclaimed = await store.claim(
            service_id="ruoyi-fashion",
            nonce="nonce:0000000002",
            now_epoch_seconds=1_600,
            ttl_seconds=600,
        )
        return first.value, full.value, reclaimed.value

    assert asyncio.run(exercise_store()) == (
        "claimed",
        "capacity_exceeded",
        "claimed",
    )


async def _stream(chunks: tuple[bytes, ...]) -> AsyncIterator[bytes]:
    for chunk in chunks:
        yield chunk


async def _post_streaming_body(
    *,
    headers: dict[str, str],
    chunks: tuple[bytes, ...],
) -> httpx.Response:
    app = create_app(settings=runtime_settings(max_json_body_bytes=1024))
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://runtime.test",
    ) as client:
        return await client.post(
            "/internal/v1/requirement-analysis",
            headers=headers,
            content=_stream(chunks),
        )


@pytest.mark.parametrize(
    "headers",
    [
        {"Content-Type": "application/json"},
        {"Content-Type": "application/json", "Content-Length": "1"},
    ],
)
def test_actual_stream_bytes_over_limit_return_413_without_trusting_length(
    headers: dict[str, str],
) -> None:
    response = asyncio.run(
        _post_streaming_body(
            headers=headers,
            chunks=(b"x" * 600, b"y" * 425),
        )
    )

    assert response.status_code == 413
    assert response.json()["error"] == {
        "code": "PAYLOAD_TOO_LARGE",
        "message": "请求体超过 Runtime 允许的字节上限",
        "retryable": False,
    }


@pytest.mark.parametrize(
    "content_length",
    ["1025", "invalid", "1, 1", "9" * 4_301],
)
def test_oversized_or_malformed_declared_length_returns_413(
    content_length: str,
) -> None:
    response = asyncio.run(
        _post_streaming_body(
            headers={
                "Content-Type": "application/json",
                "Content-Length": content_length,
            },
            chunks=(b"{}",),
        )
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "PAYLOAD_TOO_LARGE"


def test_w3c_trace_and_correlation_id_are_propagated() -> None:
    trace_id = "4bf92f3577b34da6a3ce929d0e0e4736"
    traceparent = f"00-{trace_id}-00f067aa0ba902b7-01"

    with TestClient(create_app(settings=RuntimeSettings())) as client:
        response = client.get(
            "/health",
            headers={
                "traceparent": traceparent,
                "X-Correlation-ID": "run:correlation:0001",
            },
        )

    assert response.status_code == 200
    assert response.headers["x-correlation-id"] == "run:correlation:0001"
    assert response.headers["traceparent"].split("-")[1] == trace_id


def test_invalid_correlation_id_is_replaced() -> None:
    with TestClient(create_app(settings=RuntimeSettings())) as client:
        response = client.get(
            "/health",
            headers={"X-Correlation-ID": "contains whitespace"},
        )

    correlation_id = response.headers["x-correlation-id"]
    assert correlation_id != "contains whitespace"
    assert " " not in correlation_id


def test_structured_formatter_uses_allowlist_and_redacts_unapproved_fields() -> None:
    record = logging.LogRecord(
        name="fashion_ai.runtime",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="secret body should not be serialized",
        args=(),
        exc_info=None,
    )
    record.event = "runtime.request.completed"
    record.request_id = "00000000-0000-4000-8000-000000000001"
    record.run_id = "run-001"
    record.source_text = "客户敏感需求原文"
    record.authorization = "Bearer must-not-leak"
    record.signature = "v1=must-not-leak"
    record.secret = "must-not-leak"

    payload = json.loads(StructuredJsonFormatter().format(record))

    assert payload["event"] == "runtime.request.completed"
    assert payload["request_id"] == "00000000-0000-4000-8000-000000000001"
    assert payload["run_id"] == "run-001"
    assert "source_text" not in payload
    assert "authorization" not in payload
    assert "signature" not in payload
    assert "secret" not in payload
    assert "msg" not in payload


def test_otel_foundation_has_no_exporter_and_provider_remains_disabled() -> None:
    app = create_app(settings=RuntimeSettings())

    assert app.state.telemetry.exporter_configured is False
    assert app.state.settings.provider_mode.value == "disabled"


def test_unmatched_and_pre_auth_paths_use_bounded_telemetry_labels() -> None:
    class MatchedRoute:
        path = "/internal/v1/runs/{run_id}"

    assert route_template_label({"route": MatchedRoute(), "path": "/ignored"}) == (
        "/internal/v1/runs/{run_id}"
    )
    assert route_template_label({"path": "/internal/v1/runs/customer-controlled"}) == (
        "/internal/*"
    )
    assert route_template_label({"path": "/customer-controlled"}) == "/unmatched"
