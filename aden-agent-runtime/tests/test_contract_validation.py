from __future__ import annotations

import json
from pathlib import Path

import pytest

from aden_agent_runtime.contracts import (
    ContractValidationError,
    validate_instance_file,
    validate_schema_file,
)


def _write_json(path: Path, value: object) -> Path:
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def test_local_contract_validator_accepts_valid_instance(tmp_path: Path) -> None:
    schema = _write_json(
        tmp_path / "schema.json",
        {
            "type": "object",
            "properties": {"schemaVersion": {"const": "experimental-1"}},
            "required": ["schemaVersion"],
            "additionalProperties": False,
        },
    )
    instance = _write_json(tmp_path / "instance.json", {"schemaVersion": "experimental-1"})

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
        {"$defs": {"version": {"type": "string", "const": "experimental-1"}}},
    )
    schema = _write_json(
        tmp_path / "agent.schema.json",
        {
            "$defs": {
                "AgentJob": {
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
    instance = _write_json(tmp_path / "instance.json", {"version": "experimental-1"})

    validate_instance_file(schema, instance, "/$defs/AgentJob")
