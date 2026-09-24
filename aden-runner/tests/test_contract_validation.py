from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from aden_runner.contracts import (
    ContractValidationError,
    validate_instance_file,
    validate_schema_file,
)
from aden_runner.protocol import (
    ClaimResponse,
    DeliveryHeartbeatResponse,
    ReceiptResponse,
    SessionResponse,
)

CONTRACT_ROOT = Path(__file__).resolve().parents[2] / "contracts" / "aden"


def _example(name: str) -> object:
    path = CONTRACT_ROOT / "examples" / "current" / "runner" / name
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, value: object) -> Path:
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def test_local_contract_validator_accepts_valid_instance(tmp_path: Path) -> None:
    schema = _write_json(
        tmp_path / "schema.json",
        {
            "type": "object",
            "properties": {"schemaVersion": {"const": "1"}},
            "required": ["schemaVersion"],
            "additionalProperties": False,
        },
    )
    instance = _write_json(tmp_path / "instance.json", {"schemaVersion": "1"})

    validate_schema_file(schema)
    validate_instance_file(schema, instance)


def test_local_contract_validator_rejects_unknown_field(tmp_path: Path) -> None:
    schema = _write_json(
        tmp_path / "schema.json",
        {"type": "object", "properties": {}, "additionalProperties": False},
    )
    instance = _write_json(tmp_path / "instance.json", {"unexpected": True})

    with pytest.raises(ContractValidationError, match="unexpected"):
        validate_instance_file(schema, instance)


def test_local_contract_validator_rejects_remote_ref(tmp_path: Path) -> None:
    schema = _write_json(tmp_path / "schema.json", {"$ref": "https://invalid.test/schema"})

    with pytest.raises(ContractValidationError, match="禁止非本地相对"):
        validate_schema_file(schema)


def test_local_contract_validator_resolves_relative_ref(tmp_path: Path) -> None:
    _write_json(
        tmp_path / "common.schema.json",
        {"$defs": {"version": {"type": "string", "const": "1"}}},
    )
    schema = _write_json(
        tmp_path / "task.schema.json",
        {
            "$defs": {
                "Task": {
                    "type": "object",
                    "properties": {
                        "version": {"$ref": "./common.schema.json#/$defs/version"}
                    },
                    "required": ["version"],
                    "additionalProperties": False,
                }
            }
        },
    )
    instance = _write_json(tmp_path / "instance.json", {"version": "1"})

    validate_instance_file(schema, instance, "/$defs/Task")


@pytest.mark.parametrize(
    ("model", "example"),
    [
        (SessionResponse, "session-response.json"),
        (ClaimResponse, "claim-response-long-max-fence.json"),
        (DeliveryHeartbeatResponse, "delivery-heartbeat-cancel-response.json"),
        (ReceiptResponse, "receipt-response.json"),
    ],
)
def test_protocol_models_consume_canonical_runner_examples(
    model: type[SessionResponse | ClaimResponse | DeliveryHeartbeatResponse | ReceiptResponse],
    example: str,
) -> None:
    parsed = model.model_validate(_example(example))
    assert parsed.model_dump(by_alias=True, mode="json")


def test_protocol_models_reject_wire_int64_numbers_and_unknown_fields() -> None:
    claim = _example("claim-response-long-max-fence.json")
    assert isinstance(claim, dict)
    claim["sessionEpoch"] = 9_007_199_254_740_992
    with pytest.raises(ValidationError, match="十进制字符串"):
        ClaimResponse.model_validate(claim)

    receipt = _example("receipt-response.json")
    assert isinstance(receipt, dict)
    receipt["unexpected"] = True
    with pytest.raises(ValidationError, match="Extra inputs"):
        ReceiptResponse.model_validate(receipt)


def test_protocol_models_reject_unknown_state_numeric_datetime_and_duplicate_cancel_ids() -> None:
    receipt = _example("receipt-response.json")
    assert isinstance(receipt, dict)
    receipt["deliveryState"] = "UNKNOWN"
    with pytest.raises(ValidationError):
        ReceiptResponse.model_validate(receipt)

    session = _example("session-response.json")
    assert isinstance(session, dict)
    session["issuedAt"] = 1_789_171_200
    with pytest.raises(ValidationError, match="JSON 字符串"):
        SessionResponse.model_validate(session)

    heartbeat = _example("delivery-heartbeat-cancel-response.json")
    assert isinstance(heartbeat, dict)
    duplicate: dict[str, object] = {
        "sessionId": SESSION_ID,
        "sessionEpoch": "1",
        "state": "ACTIVE",
        "expiresAt": heartbeat["leaseUntil"],
        "serverTime": heartbeat["serverTime"],
        "cancelDeliveryIds": [heartbeat["deliveryId"], heartbeat["deliveryId"]],
    }
    from aden_runner.protocol import SessionHeartbeatResponse

    with pytest.raises(ValidationError, match="不允许重复"):
        SessionHeartbeatResponse.model_validate(duplicate)


SESSION_ID = "55555555-5555-4555-8555-555555555555"
