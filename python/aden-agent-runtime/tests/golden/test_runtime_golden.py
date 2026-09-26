from __future__ import annotations

from datetime import UTC, datetime

from aden_agent_runtime.client import FakeAgentTransport, SubmitDisposition
from aden_agent_runtime.contracts import AgentJob
from aden_agent_runtime.model_ports import FakeModelPort
from aden_agent_runtime.runtime import AgentRuntime, AttemptState
from aden_agent_runtime.tools import ToolGateway

NOW = datetime(2099, 9, 12, 12, 1, tzinfo=UTC)


def _run(job: AgentJob) -> object:
    result = AgentRuntime(
        FakeModelPort(),
        FakeAgentTransport(job),
        ToolGateway(frozenset()),
        clock=lambda: NOW,
    ).run(job)
    assert result.state is AttemptState.SUBMITTED
    assert result.transitions == (
        AttemptState.CLAIMED,
        AttemptState.VALIDATED,
        AttemptState.MODEL_COMPLETED,
        AttemptState.CANDIDATE_VALIDATED,
        AttemptState.SUBMITTED,
    )
    assert result.disposition is SubmitDisposition.ACCEPTED
    return result.candidate.model_dump(by_alias=True, mode="json")


def test_same_fixture_produces_byte_stable_candidate(job: AgentJob) -> None:
    first = _run(job)
    second = _run(job)
    assert first == second


def test_empty_context_returns_stable_no_action(job: AgentJob) -> None:
    empty = job.model_copy(
        update={"context_view": job.context_view.model_copy(update={"facts": ()})}
    )
    result = AgentRuntime(
        FakeModelPort(),
        FakeAgentTransport(empty),
        ToolGateway(frozenset()),
        clock=lambda: NOW,
    ).run(empty)
    assert result.candidate.kind == "NO_ACTION"
    assert result.candidate.reason_code == "NO_CONTEXT"
    assert result.candidate.evidence == ()
