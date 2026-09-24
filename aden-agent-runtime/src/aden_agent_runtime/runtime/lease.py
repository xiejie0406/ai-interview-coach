"""独立于模型调用的取消、deadline、租约与 fencing 提交门。"""

from datetime import datetime

from aden_agent_runtime.client import ControlSnapshot
from aden_agent_runtime.contracts import AgentJob


class AttemptStoppedError(RuntimeError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class LeaseSupervisor:
    def assert_active(self, job: AgentJob, control: ControlSnapshot, now: datetime) -> None:
        if control.canceled:
            raise AttemptStoppedError("AGENT_JOB_CANCELED")
        if control.fencing_token != job.fencing_token:
            raise AttemptStoppedError("AGENT_FENCING_TOKEN_STALE")
        if now >= control.lease_until:
            raise AttemptStoppedError("AGENT_LEASE_LOST")
        if now >= job.run_spec.deadline_at:
            raise AttemptStoppedError("AGENT_DEADLINE_EXCEEDED")
