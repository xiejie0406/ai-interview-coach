"""单次离线 Agent attempt 的确定性编排。"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum

from aden_agent_runtime.agents import AgentRegistry
from aden_agent_runtime.client import FakeAgentTransport, SubmitDisposition
from aden_agent_runtime.contracts import AgentJob, Candidate
from aden_agent_runtime.model_ports import ModelAgentPort
from aden_agent_runtime.policy import AgentPolicy
from aden_agent_runtime.tools import ToolGateway

from .lease import LeaseSupervisor


class AttemptState(StrEnum):
    CLAIMED = "CLAIMED"
    VALIDATED = "VALIDATED"
    MODEL_COMPLETED = "MODEL_COMPLETED"
    CANDIDATE_VALIDATED = "CANDIDATE_VALIDATED"
    SUBMITTED = "SUBMITTED"


@dataclass(frozen=True, slots=True)
class AttemptResult:
    state: AttemptState
    transitions: tuple[AttemptState, ...]
    disposition: SubmitDisposition
    candidate: Candidate


class AgentRuntime:
    def __init__(
        self,
        model: ModelAgentPort,
        transport: FakeAgentTransport,
        tools: ToolGateway,
        *,
        clock: Callable[[], datetime] | None = None,
        policy: AgentPolicy | None = None,
        supervisor: LeaseSupervisor | None = None,
        registry: AgentRegistry | None = None,
    ):
        self._model = model
        self._transport = transport
        self._tools = tools
        self._clock = clock or (lambda: datetime.now(UTC))
        self._policy = policy or AgentPolicy()
        self._supervisor = supervisor or LeaseSupervisor()
        self._registry = registry or AgentRegistry()

    def run(self, job: AgentJob) -> AttemptResult:
        transitions = [AttemptState.CLAIMED]
        self._policy.validate_job(job)
        self._registry.require_current_executable(job.run_spec.agent_family)
        self._supervisor.assert_active(job, self._transport.control(), self._clock())
        transitions.append(AttemptState.VALIDATED)
        allowed_tools = self._tools.effective_tools(job.tool_manifest)
        draft = self._model.generate(job, allowed_tools)
        transitions.append(AttemptState.MODEL_COMPLETED)
        # 模型调用迟到时重新读取控制面; 取消、失租、过期或旧 fence 均不得提交。
        self._supervisor.assert_active(job, self._transport.control(), self._clock())
        candidate = self._policy.build_candidate(job, draft, self._clock())
        transitions.append(AttemptState.CANDIDATE_VALIDATED)
        self._supervisor.assert_active(job, self._transport.control(), self._clock())
        disposition = self._transport.submit(candidate)
        transitions.append(AttemptState.SUBMITTED)
        return AttemptResult(
            AttemptState.SUBMITTED,
            tuple(transitions),
            disposition,
            candidate,
        )
