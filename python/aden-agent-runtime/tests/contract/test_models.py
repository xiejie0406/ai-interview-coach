from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from aden_agent_runtime.contracts import AgentJob
from aden_agent_runtime.contracts.validation import validate_instance_file


def test_contract_example_passes_json_schema_and_pydantic() -> None:
    repository = Path(__file__).parents[4]
    schema = repository / "contracts/aden/schemas/experimental/agent-provider-disabled.schema.json"
    example = repository / "contracts/aden/examples/experimental/agent-job-provider-disabled.json"
    validate_instance_file(schema, example, "/$defs/AgentJob")
    parsed = AgentJob.model_validate_json(example.read_text(encoding="utf-8"))
    assert parsed.job_version == "9007199254740992"
    assert parsed.run_spec.provider_mode == "provider_disabled"


@pytest.mark.parametrize(
    ("path", "value"),
    [
        (("jobVersion",), 1),
        (("fencingToken",), "01"),
        (("runSpec", "providerMode"), "openai"),
        (("contextView", "createdAt"), 1_789_236_000),
    ],
)
def test_wire_types_fail_closed(path: tuple[str, ...], value: object) -> None:
    fixture = Path(__file__).parents[1] / "fixtures/agent-job.json"
    raw: dict[str, object] = json.loads(fixture.read_text(encoding="utf-8"))
    current: dict[str, object] = raw
    for key in path[:-1]:
        current = current[key]  # type: ignore[assignment]
    current[path[-1]] = value
    with pytest.raises(ValidationError):
        AgentJob.model_validate(raw)


def test_unknown_field_is_rejected() -> None:
    fixture = Path(__file__).parents[1] / "fixtures/agent-job.json"
    raw: dict[str, object] = json.loads(fixture.read_text(encoding="utf-8"))
    raw["unexpected"] = True
    with pytest.raises(ValidationError, match="Extra inputs"):
        AgentJob.model_validate(raw)
