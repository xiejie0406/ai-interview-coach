"""只保存测试 attempt 控制状态的离线 transport，不监听端口、不连接数据库。"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum

from aden_agent_runtime.contracts import AgentJob, Candidate


class SubmitDisposition(StrEnum):
    ACCEPTED = "ACCEPTED"
    REPLAYED = "REPLAYED"


class CandidateConflictError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class ControlSnapshot:
    canceled: bool
    lease_until: datetime
    fencing_token: str


class FakeAgentTransport:
    def __init__(self, job: AgentJob):
        self._canceled = False
        self._lease_until = job.lease_until
        self._fencing_token = job.fencing_token
        self._submitted_hash: str | None = None
        self.submission_count = 0

    def control(self) -> ControlSnapshot:
        return ControlSnapshot(self._canceled, self._lease_until, self._fencing_token)

    def cancel(self) -> None:
        self._canceled = True

    def expire_lease(self, at: datetime) -> None:
        self._lease_until = at.astimezone(UTC)

    def rotate_fence(self, fencing_token: str) -> None:
        self._fencing_token = fencing_token

    def submit(self, candidate: Candidate) -> SubmitDisposition:
        if self._submitted_hash is None:
            self._submitted_hash = candidate.result_hash
            self.submission_count += 1
            return SubmitDisposition.ACCEPTED
        if self._submitted_hash == candidate.result_hash:
            return SubmitDisposition.REPLAYED
        raise CandidateConflictError("AGENT_CANDIDATE_HASH_CONFLICT")
