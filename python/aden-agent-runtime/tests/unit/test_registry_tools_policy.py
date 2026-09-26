from __future__ import annotations

from datetime import UTC, datetime

import pytest
from pydantic import ValidationError

from aden_agent_runtime.agents import AgentRegistry
from aden_agent_runtime.contracts import AgentJob, ToolDefinition
from aden_agent_runtime.model_ports import ModelDraft
from aden_agent_runtime.policy import AgentPolicy, AgentPolicyError
from aden_agent_runtime.tools import ToolGateway, ToolPolicyError


def test_registry_contains_three_separate_disabled_product_families() -> None:
    families = AgentRegistry().product_families()
    assert [family.name for family in families] == [
        "CustomerServiceAgent",
        "SourcingAgent",
        "CatalogUnderstandingAgent",
    ]
    assert all(not family.executable_in_current_feature for family in families)
    synthetic = AgentRegistry().require_current_executable("SYNTHETIC_CORE")
    assert synthetic.executable_in_current_feature


def test_tool_scope_is_intersection_and_unauthorized_call_is_rejected(job: AgentJob) -> None:
    read = ToolDefinition.model_validate(
        {
            "name": "context.lookup",
            "effect": "read",
            "inputSchemaRef": "schema:context/input-v1",
            "outputSchemaRef": "schema:context/output-v1",
            "maxCalls": 1,
        }
    )
    proposal = ToolDefinition.model_validate(
        {
            "name": "proposal.compose",
            "effect": "proposal",
            "inputSchemaRef": "schema:proposal/input-v1",
            "outputSchemaRef": "schema:proposal/output-v1",
            "maxCalls": 1,
        }
    )
    manifest = job.tool_manifest.model_copy(update={"tools": (read, proposal)})
    gateway = ToolGateway(frozenset({"context.lookup"}))
    effective = gateway.effective_tools(manifest)
    assert effective == frozenset({"context.lookup"})
    with pytest.raises(ToolPolicyError, match="AGENT_TOOL_NOT_AUTHORIZED"):
        gateway.require_call("proposal.compose", effective)
    with pytest.raises(ValidationError):
        ToolDefinition.model_validate(
            {
                "name": "external.write",
                "effect": "external_write",
                "inputSchemaRef": "https://outside.invalid/input",
                "outputSchemaRef": "schema:outside/output-v1",
                "maxCalls": 1,
            }
        )


def test_unknown_evidence_and_sensitive_summary_are_rejected(job: AgentJob) -> None:
    policy = AgentPolicy()
    now = datetime(2099, 9, 12, 12, 1, tzinfo=UTC)
    unknown = ModelDraft(
        kind="PROPOSAL",
        summary="合成摘要",
        evidence_fact_ids=("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",),
    )
    with pytest.raises(AgentPolicyError, match="AGENT_EVIDENCE_NOT_IN_CONTEXT"):
        policy.build_candidate(job, unknown, now)
    sensitive = ModelDraft(
        kind="PROPOSAL",
        summary="credential 不得进入候选",
        evidence_fact_ids=(job.context_view.facts[0].fact_id,),
    )
    with pytest.raises(AgentPolicyError, match="AGENT_SENSITIVE_CANDIDATE_REJECTED"):
        policy.build_candidate(job, sensitive, now)
