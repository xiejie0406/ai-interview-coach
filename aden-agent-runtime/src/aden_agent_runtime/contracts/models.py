"""与 experimental/agent-provider-disabled.schema.json 对齐的严格 Pydantic DTO。"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    AfterValidator,
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    TypeAdapter,
)


def _canonical_int64(value: object) -> str:
    if not isinstance(value, str) or not value.isascii() or not value.isdigit():
        raise ValueError("int64 必须使用 canonical 十进制字符串")
    parsed = int(value)
    if str(parsed) != value or parsed < 0 or parsed > 9_223_372_036_854_775_807:
        raise ValueError("不是 non-negative canonical int64")
    return value


def _canonical_uuid(value: str) -> str:
    if str(UUID(value)) != value:
        raise ValueError("不是 canonical UUID")
    return value


def _datetime_string(value: object) -> datetime:
    if not isinstance(value, str):
        raise ValueError("date-time 必须使用 JSON 字符串")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("date-time 字符串不合法") from exc


def _utc_datetime(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() != UTC.utcoffset(value):
        raise ValueError("date-time 必须带 UTC 时区")
    return value.astimezone(UTC)


CanonicalInt64 = Annotated[str, BeforeValidator(_canonical_int64)]
CanonicalUuid = Annotated[str, AfterValidator(_canonical_uuid)]
UtcDateTime = Annotated[
    datetime,
    BeforeValidator(_datetime_string),
    AfterValidator(_utc_datetime),
]
Sha256 = Annotated[str, Field(pattern=r"^[a-f0-9]{64}$")]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True, strict=True)


class RunSpec(StrictModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    run_spec_id: CanonicalUuid = Field(alias="runSpecId")
    run_spec_version: CanonicalInt64 = Field(alias="runSpecVersion")
    agent_family: Literal["SYNTHETIC_CORE"] = Field(alias="agentFamily")
    provider_mode: Literal["provider_disabled"] = Field(alias="providerMode")
    deadline_at: UtcDateTime = Field(alias="deadlineAt")
    max_candidate_bytes: int = Field(alias="maxCandidateBytes", ge=256, le=1_048_576)
    tool_manifest_hash: Sha256 = Field(alias="toolManifestHash")


class ContextFact(StrictModel):
    fact_id: CanonicalUuid = Field(alias="factId")
    name: str = Field(pattern=r"^[a-z][a-z0-9_.-]{1,63}$")
    value: str | bool | int | None
    source_ref: str = Field(alias="sourceRef", min_length=1, max_length=200)
    source_version: CanonicalInt64 = Field(alias="sourceVersion")


class ContextView(StrictModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    context_view_id: CanonicalUuid = Field(alias="contextViewId")
    workspace_id: CanonicalUuid = Field(alias="workspaceId")
    task_id: CanonicalUuid = Field(alias="taskId")
    context_version: CanonicalInt64 = Field(alias="contextVersion")
    facts: tuple[ContextFact, ...] = Field(max_length=128)
    context_hash: Sha256 = Field(alias="contextHash")
    created_at: UtcDateTime = Field(alias="createdAt")


class ToolDefinition(StrictModel):
    name: str = Field(pattern=r"^[a-z][a-z0-9_.-]{2,63}$")
    effect: Literal["read", "proposal"]
    input_schema_ref: str = Field(
        alias="inputSchemaRef", pattern=r"^schema:[a-z0-9][a-z0-9._/-]{2,127}$"
    )
    output_schema_ref: str = Field(
        alias="outputSchemaRef", pattern=r"^schema:[a-z0-9][a-z0-9._/-]{2,127}$"
    )
    max_calls: int = Field(alias="maxCalls", ge=0, le=32)


class ToolManifest(StrictModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    manifest_id: CanonicalUuid = Field(alias="manifestId")
    manifest_version: CanonicalInt64 = Field(alias="manifestVersion")
    provider_mode: Literal["provider_disabled"] = Field(alias="providerMode")
    tools: tuple[ToolDefinition, ...] = Field(max_length=32)
    manifest_hash: Sha256 = Field(alias="manifestHash")


class AgentJob(StrictModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    job_id: CanonicalUuid = Field(alias="jobId")
    attempt_id: CanonicalUuid = Field(alias="attemptId")
    workspace_id: CanonicalUuid = Field(alias="workspaceId")
    task_id: CanonicalUuid = Field(alias="taskId")
    job_version: CanonicalInt64 = Field(alias="jobVersion")
    fencing_token: CanonicalInt64 = Field(alias="fencingToken")
    lease_until: UtcDateTime = Field(alias="leaseUntil")
    run_spec: RunSpec = Field(alias="runSpec")
    context_view: ContextView = Field(alias="contextView")
    tool_manifest: ToolManifest = Field(alias="toolManifest")


class EvidenceRef(StrictModel):
    fact_id: CanonicalUuid = Field(alias="factId")
    source_ref: str = Field(alias="sourceRef", min_length=1, max_length=200)
    source_version: CanonicalInt64 = Field(alias="sourceVersion")


class CandidateBase(StrictModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    candidate_id: CanonicalUuid = Field(alias="candidateId")
    job_id: CanonicalUuid = Field(alias="jobId")
    attempt_id: CanonicalUuid = Field(alias="attemptId")
    fencing_token: CanonicalInt64 = Field(alias="fencingToken")
    evidence: tuple[EvidenceRef, ...] = Field(max_length=32)
    result_hash: Sha256 = Field(alias="resultHash")
    created_at: UtcDateTime = Field(alias="createdAt")


class NoActionCandidate(CandidateBase):
    kind: Literal["NO_ACTION"]
    reason_code: str = Field(alias="reasonCode", pattern=r"^[A-Z][A-Z0-9_]{2,63}$")


class ProposalCandidate(CandidateBase):
    kind: Literal["PROPOSAL"]
    proposal_type: Literal["SYNTHETIC_SUMMARY"] = Field(alias="proposalType")
    summary: str = Field(min_length=1, max_length=1000)
    evidence: tuple[EvidenceRef, ...] = Field(min_length=1, max_length=32)


type Candidate = Annotated[
    NoActionCandidate | ProposalCandidate,
    Field(discriminator="kind"),
]
_CANDIDATE_ADAPTER: TypeAdapter[Candidate] = TypeAdapter(Candidate)


def validate_candidate(value: object) -> Candidate:
    return _CANDIDATE_ADAPTER.validate_python(value, strict=True)
