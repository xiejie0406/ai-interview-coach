"""业务自有模型端口；Provider SDK 类型不得穿透该边界。"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Literal, Protocol

from aden_agent_runtime.contracts import AgentJob


@dataclass(frozen=True, slots=True)
class ModelDraft:
    kind: Literal["NO_ACTION", "PROPOSAL"]
    evidence_fact_ids: tuple[str, ...] = ()
    summary: str | None = None
    reason_code: str | None = None


class ModelAgentPort(Protocol):
    def generate(self, job: AgentJob, allowed_tools: frozenset[str]) -> ModelDraft: ...


class ProviderDisabledError(RuntimeError):
    pass


class DisabledModelPort:
    def generate(self, job: AgentJob, allowed_tools: frozenset[str]) -> ModelDraft:
        del job, allowed_tools
        raise ProviderDisabledError("provider_disabled：真实模型调用未启用")


class FakeModelPort:
    """固定算法或显式工厂；相同输入得到相同 draft，且从不发起网络请求。"""

    def __init__(self, factory: Callable[[AgentJob, frozenset[str]], ModelDraft] | None = None):
        self._factory = factory
        self.calls = 0

    def generate(self, job: AgentJob, allowed_tools: frozenset[str]) -> ModelDraft:
        self.calls += 1
        if self._factory is not None:
            return self._factory(job, allowed_tools)
        if not job.context_view.facts:
            return ModelDraft(kind="NO_ACTION", reason_code="NO_CONTEXT")
        return ModelDraft(
            kind="PROPOSAL",
            summary=f"基于 {len(job.context_view.facts)} 条合成事实生成的离线候选。",
            evidence_fact_ids=(job.context_view.facts[0].fact_id,),
        )
