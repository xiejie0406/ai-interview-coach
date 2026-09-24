"""与 contracts/aden/schemas/current/runner.schema.json 对齐的严格模型。"""

from __future__ import annotations

import json
from datetime import datetime
from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import AfterValidator, BaseModel, BeforeValidator, ConfigDict, Field


def _canonical_int64(value: object) -> int:
    if not isinstance(value, str) or not value.isascii() or not value.isdigit():
        raise ValueError("必须使用十进制字符串传输 int64")
    parsed = int(value)
    if str(parsed) != value or parsed < 0 or parsed > 9_223_372_036_854_775_807:
        raise ValueError("不是 canonical non-negative int64")
    return parsed


def _canonical_uuid(value: str) -> str:
    if str(UUID(value)) != value:
        raise ValueError("不是 canonical UUID")
    return value


def _datetime_string(value: object) -> object:
    if not isinstance(value, str):
        raise ValueError("date-time 必须使用 JSON 字符串传输")
    return value


def _unique_uuids(value: tuple[str, ...]) -> tuple[str, ...]:
    if len(set(value)) != len(value):
        raise ValueError("UUID 列表不允许重复值")
    return value


CanonicalInt64 = Annotated[int, BeforeValidator(_canonical_int64)]
CanonicalUuid = Annotated[str, AfterValidator(_canonical_uuid)]
UtcDateTime = Annotated[datetime, BeforeValidator(_datetime_string)]
UniqueUuidTuple = Annotated[tuple[CanonicalUuid, ...], AfterValidator(_unique_uuids)]

DeliveryState = Literal[
    "READY", "LEASED", "RUNNING", "COMPLETED", "FAILED_RETRYABLE",
    "FAILED_FINAL", "CANCELED", "OUTCOME_UNKNOWN",
]
TaskState = Literal[
    "DRAFT", "VALIDATING", "QUEUED", "RUNNING", "WAITING_USER",
    "WAITING_EXTERNAL", "CANCEL_REQUESTED", "SUCCEEDED", "FAILED", "CANCELED",
]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)


class SessionResponse(StrictModel):
    workspace_id: CanonicalUuid = Field(alias="workspaceId")
    runner_id: CanonicalUuid = Field(alias="runnerId")
    session_id: CanonicalUuid = Field(alias="sessionId")
    session_token: str = Field(
        alias="sessionToken",
        min_length=64,
        max_length=512,
        pattern=r"^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$",
        repr=False,
    )
    session_epoch: CanonicalInt64 = Field(alias="sessionEpoch", ge=1)
    state: Literal["ACTIVE"]
    expires_at: UtcDateTime = Field(alias="expiresAt")
    heartbeat_after_seconds: int = Field(alias="heartbeatAfterSeconds", ge=1, le=300)
    issued_at: UtcDateTime = Field(alias="issuedAt")


class SyntheticRunInput(StrictModel):
    fixture_id: str = Field(alias="fixtureId", pattern=r"^fixture:[a-z0-9][a-z0-9._-]{2,63}$")
    instruction: str = Field(min_length=1, max_length=1000)
    expected_outcome: Literal["SUCCEED", "CANCEL_AT_SAFE_POINT"] = Field(alias="expectedOutcome")


class TaskPackage(StrictModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    task_id: CanonicalUuid = Field(alias="taskId")
    step_id: CanonicalUuid = Field(alias="stepId")
    task_type: Literal["SYNTHETIC_CORE"] = Field(alias="taskType")
    capability_code: Literal["CORE"] = Field(alias="capabilityCode")
    attempt_no: int = Field(alias="attemptNo", ge=1, le=100)
    package_hash: str = Field(alias="packageHash", pattern=r"^[a-f0-9]{64}$")
    input: SyntheticRunInput
    external_actions_enabled: Literal[False] = Field(alias="externalActionsEnabled")
    deadline_at: UtcDateTime = Field(alias="deadlineAt")


class Delivery(StrictModel):
    delivery_id: CanonicalUuid = Field(alias="deliveryId")
    state: Literal["LEASED"]
    fence_token: CanonicalInt64 = Field(alias="fencingToken", ge=1)
    lease_until: UtcDateTime = Field(alias="leaseUntil")
    task_package: TaskPackage = Field(alias="taskPackage")

    @property
    def task_id(self) -> str:
        return self.task_package.task_id

    @property
    def step_id(self) -> str:
        return self.task_package.step_id

    @property
    def attempt_no(self) -> int:
        return self.task_package.attempt_no

    @property
    def task_package_hash(self) -> str:
        return self.task_package.package_hash

    @property
    def task_package_json(self) -> str:
        return json.dumps(
            self.task_package.model_dump(by_alias=True, mode="json"),
            ensure_ascii=False,
            separators=(",", ":"),
        )


class ClaimResponse(StrictModel):
    claim_request_id: CanonicalUuid = Field(alias="claimRequestId")
    session_id: CanonicalUuid = Field(alias="sessionId")
    session_epoch: CanonicalInt64 = Field(alias="sessionEpoch", ge=1)
    items: tuple[Delivery, ...] = Field(max_length=16)
    server_time: UtcDateTime = Field(alias="serverTime")

    @property
    def deliveries(self) -> tuple[Delivery, ...]:
        return self.items


class SessionHeartbeatResponse(StrictModel):
    session_id: CanonicalUuid = Field(alias="sessionId")
    session_epoch: CanonicalInt64 = Field(alias="sessionEpoch", ge=1)
    state: Literal["ACTIVE"]
    expires_at: UtcDateTime = Field(alias="expiresAt")
    server_time: UtcDateTime = Field(alias="serverTime")
    cancel_delivery_ids: UniqueUuidTuple = Field(alias="cancelDeliveryIds", max_length=32)


class DeliveryHeartbeatResponse(StrictModel):
    delivery_id: CanonicalUuid = Field(alias="deliveryId")
    state: DeliveryState
    fence_token: CanonicalInt64 = Field(alias="fencingToken", ge=1)
    lease_until: UtcDateTime = Field(alias="leaseUntil")
    cancel_requested: bool = Field(alias="cancelRequested")
    server_time: UtcDateTime = Field(alias="serverTime")


class ReceiptResponse(StrictModel):
    receipt_id: CanonicalUuid = Field(alias="receiptId")
    disposition: Literal["ACCEPTED", "REPLAYED"]
    delivery_id: CanonicalUuid = Field(alias="deliveryId")
    delivery_state: DeliveryState = Field(alias="deliveryState")
    task_id: CanonicalUuid = Field(alias="taskId")
    task_state: TaskState = Field(alias="taskState")
    task_version: CanonicalInt64 = Field(alias="taskVersion", ge=1)
    last_receipt_sequence: CanonicalInt64 = Field(alias="lastReceiptSequence", ge=1)
    accepted_at: UtcDateTime = Field(alias="acceptedAt")
    correlation_id: CanonicalUuid = Field(alias="correlationId")


ReceiptPayload = dict[str, Any]
