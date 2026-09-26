"""Runtime 契约测试的确定性签名辅助函数。"""

import hashlib
import json
import time
from typing import Any, cast
from uuid import UUID, uuid4

from fastapi.testclient import TestClient
from httpx import Response

from fashion_ai.api.security import build_canonical_request, sign_canonical_request
from fashion_ai.settings import HmacKey, RuntimeSettings, ServiceAuthSettings

TEST_ACTIVE_KEY_ID = "runtime-active-2026-09"
TEST_ACTIVE_SECRET = b"0123456789abcdef0123456789abcdef"
TEST_PREVIOUS_KEY_ID = "runtime-previous-2026-08"
TEST_PREVIOUS_SECRET = b"abcdef0123456789abcdef0123456789"
TEST_TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"


def runtime_settings(
    *,
    max_json_body_bytes: int = 64 * 1024,
    include_previous_key: bool = False,
) -> RuntimeSettings:
    return RuntimeSettings(
        max_json_body_bytes=max_json_body_bytes,
        service_auth=ServiceAuthSettings(
            active_key=HmacKey(
                key_id=TEST_ACTIVE_KEY_ID,
                secret=TEST_ACTIVE_SECRET,
            ),
            previous_key=(
                HmacKey(
                    key_id=TEST_PREVIOUS_KEY_ID,
                    secret=TEST_PREVIOUS_SECRET,
                )
                if include_previous_key
                else None
            ),
        ),
    )


def encode_json(payload: object) -> bytes:
    return json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")


def signed_headers(
    *,
    method: str,
    path: str,
    body: bytes = b"",
    service_id: str = "ruoyi-fashion",
    audience: str = "fashion-ai-runtime",
    key_id: str = TEST_ACTIVE_KEY_ID,
    secret: bytes = TEST_ACTIVE_SECRET,
    timestamp: int | None = None,
    nonce: str | None = None,
    request_id: str | None = None,
    correlation_id: str | None = None,
    traceparent: str = TEST_TRACEPARENT,
) -> dict[str, str]:
    timestamp_text = str(int(time.time()) if timestamp is None else timestamp)
    nonce_value = nonce or str(uuid4())
    digest = hashlib.sha256(body).hexdigest()
    canonical = build_canonical_request(
        method=method,
        raw_path=path,
        service_id=service_id,
        audience=audience,
        timestamp=timestamp_text,
        nonce=nonce_value,
        content_sha256=digest,
    )
    headers = {
        "X-Fashion-Service-Id": service_id,
        "X-Fashion-Key-Id": key_id,
        "X-Fashion-Timestamp": timestamp_text,
        "X-Fashion-Nonce": nonce_value,
        "X-Fashion-Audience": audience,
        "X-Fashion-Content-SHA256": digest,
        "X-Fashion-Signature": sign_canonical_request(secret, canonical),
        "X-Request-Id": request_id or str(uuid4()),
        "X-Correlation-Id": correlation_id or f"corr:{uuid4()}",
        "traceparent": traceparent,
    }
    return headers


def signed_get(client: TestClient, path: str, **header_overrides: Any) -> Response:
    headers = signed_headers(method="GET", path=path)
    headers.update(header_overrides)
    return cast(Response, client.get(path, headers=headers))


def signed_json_post(
    client: TestClient,
    path: str,
    payload: object,
    **header_overrides: Any,
) -> Response:
    body = encode_json(payload)
    headers = signed_headers(
        method="POST",
        path=path,
        body=body,
        request_id=_valid_request_id(payload),
        correlation_id=_valid_correlation_id(payload),
    )
    headers.update(header_overrides)
    headers["Content-Type"] = "application/json"
    return cast(Response, client.post(path, content=body, headers=headers))


def _valid_request_id(payload: object) -> str | None:
    if not isinstance(payload, dict):
        return None
    value = cast(dict[object, object], payload).get("request_id")
    if not isinstance(value, str):
        return None
    try:
        return str(UUID(value))
    except ValueError:
        return None


def _valid_correlation_id(payload: object) -> str | None:
    if not isinstance(payload, dict):
        return None
    value = cast(dict[object, object], payload).get("correlation_id")
    if not isinstance(value, str):
        return None
    return value
