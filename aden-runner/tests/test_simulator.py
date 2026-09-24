from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime, timedelta
from typing import Any

import httpx
import pytest
from pydantic import ValidationError

from aden_runner.config import RunnerSettings
from aden_runner.fixtures import FixtureExecutor, PackageIntegrityError
from aden_runner.protocol.models import Delivery
from aden_runner.simulator import FaultPoint, RunnerSimulator, SimulatedCrash
from aden_runner.transport import AdenTransport

WORKSPACE_ID = "11111111-1111-4111-8111-111111111111"
RUNNER_ID = "22222222-2222-4222-8222-222222222222"
SESSION_ID = "33333333-3333-4333-8333-333333333333"
SESSION_TOKEN = f"{SESSION_ID}.{'s' * 43}"
DELIVERY_ID = "44444444-4444-4444-8444-444444444444"
TASK_ID = "55555555-5555-4555-8555-555555555555"
STEP_ID = "66666666-6666-4666-8666-666666666666"
CLAIM_ID = "77777777-7777-4777-8777-777777777777"
CORRELATION_ID = "88888888-8888-4888-8888-888888888888"


def package(now: datetime) -> tuple[dict[str, Any], str]:
    deadline = (now + timedelta(minutes=10)).isoformat().replace("+00:00", "Z")
    body: dict[str, Any] = {
        "schemaVersion": 1,
        "taskId": TASK_ID,
        "stepId": STEP_ID,
        "taskType": "SYNTHETIC_CORE",
        "capabilityCode": "CORE",
        "attemptNo": 1,
        "input": {
            "fixtureId": "fixture:success",
            "instruction": "执行固定合成数据",
            "expectedOutcome": "SUCCEED",
        },
        "externalActionsEnabled": False,
        "deadlineAt": deadline,
    }
    package_hash = hashlib.sha256(
        json.dumps(body, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
    ).hexdigest()
    return {**body, "packageHash": package_hash}, package_hash


def session_response(now: datetime) -> dict[str, Any]:
    return {
        "workspaceId": WORKSPACE_ID,
        "runnerId": RUNNER_ID,
        "sessionId": SESSION_ID,
        "sessionToken": SESSION_TOKEN,
        "sessionEpoch": "1",
        "state": "ACTIVE",
        "expiresAt": (now + timedelta(minutes=15)).isoformat(),
        "heartbeatAfterSeconds": 15,
        "issuedAt": now.isoformat(),
    }


def claim_response(now: datetime, claim_id: str = CLAIM_ID) -> dict[str, Any]:
    task_package, _ = package(now)
    return {
        "claimRequestId": claim_id,
        "sessionId": SESSION_ID,
        "sessionEpoch": "1",
        "items": [{
            "deliveryId": DELIVERY_ID,
            "state": "LEASED",
            "fencingToken": "7",
            "leaseUntil": (now + timedelta(minutes=5)).isoformat(),
            "taskPackage": task_package,
        }],
        "serverTime": now.isoformat(),
    }


@pytest.mark.parametrize(
    "origin",
    ["https://example.com", "http://192.168.1.9:8080", "file:///tmp/aden", "http://localhost/x"],
)
def test_settings_reject_every_non_loopback_origin(origin: str) -> None:
    with pytest.raises(ValidationError):
        RunnerSettings(origin=origin, credential_token="credential.secret")


def test_fixture_requires_canonical_package_hash_and_external_actions_disabled() -> None:
    now = datetime.now(UTC)
    task_package, package_hash = package(now)
    delivery = Delivery.model_validate({
        "deliveryId": DELIVERY_ID,
        "state": "LEASED",
        "fencingToken": "1",
        "leaseUntil": (now + timedelta(minutes=5)).isoformat(),
        "taskPackage": task_package,
    })
    assert FixtureExecutor().execute(delivery)["externalActionsPerformed"] is False
    altered_package = delivery.task_package.model_copy(update={"package_hash": "0" * 64})
    altered = delivery.model_copy(update={"task_package": altered_package})
    assert delivery.task_package_hash == package_hash
    with pytest.raises(PackageIntegrityError):
        FixtureExecutor().execute(altered)


def test_task_package_hash_matches_java_canonical_json_vector() -> None:
    body: dict[str, Any] = {
        "schemaVersion": 1,
        "taskId": TASK_ID,
        "stepId": STEP_ID,
        "taskType": "SYNTHETIC_CORE",
        "capabilityCode": "CORE",
        "attemptNo": 1,
        "input": {
            "fixtureId": "fixture:success",
            "instruction": "deterministic",
            "expectedOutcome": "SUCCEED",
        },
        "externalActionsEnabled": False,
        "deadlineAt": "2026-09-13T00:00:00.123456Z",
    }
    canonical = json.dumps(body, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    assert hashlib.sha256(canonical.encode()).hexdigest() == (
        "77dcc9fe78bbe09e1c6ba88e3ce0061e697f4c58dfc799fb5451212e6bca1e48"
    )


def test_simulator_runs_contract_paths_and_monotonic_receipts() -> None:
    now = datetime.now(UTC)
    receipt_kinds: list[str] = []
    receipt_sequences: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.host in {"127.0.0.1", "localhost"}
        body = json.loads(request.content)
        if request.url.path.endswith("/sessions"):
            assert request.headers["authorization"] == "AdenCredential credential.secret"
            return httpx.Response(201, json=session_response(now))
        assert request.headers["authorization"] == f"AdenRunner {SESSION_TOKEN}"
        if request.url.path.endswith("/deliveries:claim"):
            assert body["claimRequestId"] == CLAIM_ID
            assert request.headers["idempotency-key"] == f"claim:{CLAIM_ID}"
            return httpx.Response(200, json=claim_response(now))
        if f"/sessions/{SESSION_ID}/heartbeats" in request.url.path:
            return httpx.Response(200, json={
                "sessionId": SESSION_ID,
                "sessionEpoch": body["sessionEpoch"],
                "state": "ACTIVE",
                "expiresAt": (now + timedelta(minutes=15)).isoformat(),
                "serverTime": now.isoformat(),
                "cancelDeliveryIds": [],
            })
        if request.url.path.endswith("/heartbeats"):
            return httpx.Response(200, json={
                "deliveryId": DELIVERY_ID,
                "state": "RUNNING",
                "fencingToken": body["fencingToken"],
                "leaseUntil": (now + timedelta(minutes=5)).isoformat(),
                "cancelRequested": False,
                "serverTime": now.isoformat(),
            })
        receipt_kinds.append(body["kind"])
        receipt_sequences.append(body["receiptSequence"])
        state = "COMPLETED" if body["kind"] == "SUCCEEDED" else "RUNNING"
        task_state = "SUCCEEDED" if body["kind"] == "SUCCEEDED" else "RUNNING"
        return httpx.Response(200, json={
            "receiptId": body["receiptId"],
            "disposition": "ACCEPTED",
            "deliveryId": DELIVERY_ID,
            "deliveryState": state,
            "taskId": TASK_ID,
            "taskState": task_state,
            "taskVersion": str(int(body["receiptSequence"]) + 3),
            "lastReceiptSequence": body["receiptSequence"],
            "acceptedAt": now.isoformat(),
            "correlationId": CORRELATION_ID,
        })

    settings = RunnerSettings(origin="http://127.0.0.1:8080", credential_token="credential.secret")
    client = httpx.Client(transport=httpx.MockTransport(handler), trust_env=False)
    simulator = RunnerSimulator(AdenTransport(settings, client))
    simulator.exchange_session()

    assert simulator.run_once(claim_request_id=CLAIM_ID) == 1
    assert receipt_kinds == ["STARTED", "PROGRESS", "SUCCEEDED"]
    assert receipt_sequences == ["1", "2", "3"]


def test_fault_script_crashes_after_claim_without_external_action() -> None:
    now = datetime.now(UTC)
    settings = RunnerSettings(origin="http://localhost:8080", credential_token="credential.secret")

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/sessions"):
            return httpx.Response(201, json=session_response(now))
        return httpx.Response(200, json={
            "claimRequestId": CLAIM_ID,
            "sessionId": SESSION_ID,
            "sessionEpoch": "1",
            "items": [],
            "serverTime": now.isoformat(),
        })

    simulator = RunnerSimulator(AdenTransport(
        settings, httpx.Client(transport=httpx.MockTransport(handler), trust_env=False)
    ))
    simulator.exchange_session()
    with pytest.raises(SimulatedCrash):
        simulator.run_once(FaultPoint.AFTER_CLAIM, CLAIM_ID)


def test_receipt_response_loss_retries_same_key_and_sequence() -> None:
    now = datetime.now(UTC)
    started_attempts: list[tuple[str, dict[str, object]]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        if request.url.path.endswith("/sessions"):
            return httpx.Response(201, json=session_response(now))
        if request.url.path.endswith("/deliveries:claim"):
            return httpx.Response(200, json=claim_response(now))
        if f"/sessions/{SESSION_ID}/heartbeats" in request.url.path:
            return httpx.Response(200, json={
                "sessionId": SESSION_ID,
                "sessionEpoch": "1",
                "state": "ACTIVE",
                "expiresAt": (now + timedelta(minutes=15)).isoformat(),
                "serverTime": now.isoformat(),
                "cancelDeliveryIds": [],
            })
        if request.url.path.endswith("/heartbeats"):
            return httpx.Response(200, json={
                "deliveryId": DELIVERY_ID,
                "state": "RUNNING",
                "fencingToken": "7",
                "leaseUntil": (now + timedelta(minutes=5)).isoformat(),
                "cancelRequested": False,
                "serverTime": now.isoformat(),
            })
        if body["kind"] == "STARTED":
            started_attempts.append((request.headers["idempotency-key"], body))
            if len(started_attempts) == 1:
                raise httpx.ReadError("synthetic response lost", request=request)
        state = "COMPLETED" if body["kind"] == "SUCCEEDED" else "RUNNING"
        task_state = "SUCCEEDED" if body["kind"] == "SUCCEEDED" else "RUNNING"
        return httpx.Response(200, json={
            "receiptId": body["receiptId"],
            "disposition": "REPLAYED" if len(started_attempts) > 1 else "ACCEPTED",
            "deliveryId": DELIVERY_ID,
            "deliveryState": state,
            "taskId": TASK_ID,
            "taskState": task_state,
            "taskVersion": str(int(body["receiptSequence"]) + 3),
            "lastReceiptSequence": body["receiptSequence"],
            "acceptedAt": now.isoformat(),
            "correlationId": CORRELATION_ID,
        })

    settings = RunnerSettings(origin="http://127.0.0.1:8080", credential_token="credential.secret")
    simulator = RunnerSimulator(AdenTransport(
        settings, httpx.Client(transport=httpx.MockTransport(handler), trust_env=False)
    ))
    simulator.exchange_session()
    with pytest.raises(httpx.ReadError):
        simulator.run_once(claim_request_id=CLAIM_ID)
    assert simulator.run_once(claim_request_id=CLAIM_ID) == 1
    assert len(started_attempts) == 2
    assert started_attempts[0] == started_attempts[1]
    assert started_attempts[0][1]["receiptSequence"] == "1"
