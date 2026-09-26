from __future__ import annotations

import socket
from collections.abc import Callable
from datetime import UTC, datetime, timedelta

import pytest

from aden_agent_runtime.client import CandidateConflictError, FakeAgentTransport, SubmitDisposition
from aden_agent_runtime.contracts import AgentJob
from aden_agent_runtime.model_ports import FakeModelPort, ModelDraft
from aden_agent_runtime.runtime import AgentRuntime, AttemptStoppedError
from aden_agent_runtime.tools import ToolGateway

NOW = datetime(2099, 9, 12, 12, 1, tzinfo=UTC)
type ControlMutation = Callable[[FakeAgentTransport], None]


def _cancel(transport: FakeAgentTransport) -> None:
    transport.cancel()


def _expire(transport: FakeAgentTransport) -> None:
    transport.expire_lease(NOW - timedelta(seconds=1))


def _rotate(transport: FakeAgentTransport) -> None:
    transport.rotate_fence("2")


MUTATIONS: list[tuple[ControlMutation, str]] = [
    (_cancel, "AGENT_JOB_CANCELED"),
    (_expire, "AGENT_LEASE_LOST"),
    (_rotate, "AGENT_FENCING_TOKEN_STALE"),
]


def _runtime(job: AgentJob, transport: FakeAgentTransport, model: FakeModelPort) -> AgentRuntime:
    return AgentRuntime(model, transport, ToolGateway(frozenset()), clock=lambda: NOW)


@pytest.mark.parametrize(
    ("mutation", "code"),
    MUTATIONS,
)
def test_late_model_result_is_discarded_after_control_change(
    job: AgentJob,
    mutation: ControlMutation,
    code: str,
) -> None:
    transport = FakeAgentTransport(job)

    def mutate_then_return(current: AgentJob, tools: frozenset[str]) -> ModelDraft:
        del current, tools
        mutation(transport)
        return ModelDraft(
            kind="PROPOSAL",
            summary="合成摘要",
            evidence_fact_ids=(job.context_view.facts[0].fact_id,),
        )

    with pytest.raises(AttemptStoppedError, match=code):
        _runtime(job, transport, FakeModelPort(mutate_then_return)).run(job)
    assert transport.submission_count == 0


def test_deadline_and_candidate_byte_budget_fail_closed(job: AgentJob) -> None:
    expired = job.model_copy(
        update={"run_spec": job.run_spec.model_copy(update={"deadline_at": NOW})}
    )
    with pytest.raises(AttemptStoppedError, match="AGENT_DEADLINE_EXCEEDED"):
        _runtime(expired, FakeAgentTransport(expired), FakeModelPort()).run(expired)

    tiny = job.model_copy(
        update={"run_spec": job.run_spec.model_copy(update={"max_candidate_bytes": 256})}
    )
    with pytest.raises(RuntimeError, match="AGENT_CANDIDATE_BUDGET_EXCEEDED"):
        _runtime(tiny, FakeAgentTransport(tiny), FakeModelPort()).run(tiny)


def test_duplicate_same_hash_replays_but_different_hash_conflicts(job: AgentJob) -> None:
    transport = FakeAgentTransport(job)
    first = _runtime(job, transport, FakeModelPort()).run(job)
    second = _runtime(job, transport, FakeModelPort()).run(job)
    assert first.disposition is SubmitDisposition.ACCEPTED
    assert second.disposition is SubmitDisposition.REPLAYED

    changed = FakeModelPort(
        lambda current, tools: ModelDraft(
            kind="PROPOSAL",
            summary="不同的合成摘要",
            evidence_fact_ids=(current.context_view.facts[0].fact_id,),
        )
    )
    with pytest.raises(CandidateConflictError, match="AGENT_CANDIDATE_HASH_CONFLICT"):
        _runtime(job, transport, changed).run(job)


def test_normal_fake_run_opens_no_network_connection(
    job: AgentJob, monkeypatch: pytest.MonkeyPatch
) -> None:
    def deny_connect(*args: object, **kwargs: object) -> None:
        del args, kwargs
        raise AssertionError("Agent Runtime 不得联网")

    monkeypatch.setattr(socket.socket, "connect", deny_connect)
    result = _runtime(job, FakeAgentTransport(job), FakeModelPort()).run(job)
    assert result.disposition is SubmitDisposition.ACCEPTED
