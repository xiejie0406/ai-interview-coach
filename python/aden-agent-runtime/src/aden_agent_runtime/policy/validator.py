"""上下文、引用、敏感字段、大小与候选 hash 的确定性校验。"""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime
from uuid import NAMESPACE_URL, uuid5

from aden_agent_runtime.contracts import (
    AgentJob,
    Candidate,
    EvidenceRef,
    NoActionCandidate,
    ProposalCandidate,
)
from aden_agent_runtime.model_ports import ModelDraft

_SENSITIVE = re.compile(r"(?:password|secret|token|credential|api[_-]?key)", re.IGNORECASE)


class AgentPolicyError(RuntimeError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class AgentPolicy:
    def validate_job(self, job: AgentJob) -> None:
        if (
            job.workspace_id != job.context_view.workspace_id
            or job.task_id != job.context_view.task_id
        ):
            raise AgentPolicyError("AGENT_CONTEXT_SCOPE_MISMATCH")
        if job.run_spec.tool_manifest_hash != job.tool_manifest.manifest_hash:
            raise AgentPolicyError("AGENT_TOOL_MANIFEST_HASH_MISMATCH")
        if job.run_spec.deadline_at > job.lease_until:
            raise AgentPolicyError("AGENT_DEADLINE_AFTER_LEASE")
        ids = [fact.fact_id for fact in job.context_view.facts]
        if len(ids) != len(set(ids)):
            raise AgentPolicyError("AGENT_DUPLICATE_FACT")
        if any(_SENSITIVE.search(fact.name) or _SENSITIVE.search(fact.source_ref)
               for fact in job.context_view.facts):
            raise AgentPolicyError("AGENT_SENSITIVE_CONTEXT_REJECTED")

    def build_candidate(self, job: AgentJob, draft: ModelDraft, now: datetime) -> Candidate:
        fact_index = {fact.fact_id: fact for fact in job.context_view.facts}
        if len(draft.evidence_fact_ids) != len(set(draft.evidence_fact_ids)):
            raise AgentPolicyError("AGENT_DUPLICATE_EVIDENCE")
        try:
            evidence = tuple(
                EvidenceRef.model_validate(
                    {
                        "factId": fact_index[fact_id].fact_id,
                        "sourceRef": fact_index[fact_id].source_ref,
                        "sourceVersion": fact_index[fact_id].source_version,
                    }
                )
                for fact_id in draft.evidence_fact_ids
            )
        except KeyError as exc:
            raise AgentPolicyError("AGENT_EVIDENCE_NOT_IN_CONTEXT") from exc

        semantic: dict[str, object]
        common: dict[str, object] = {
            "schemaVersion": 1,
            "jobId": job.job_id,
            "attemptId": job.attempt_id,
            "fencingToken": job.fencing_token,
            "kind": draft.kind,
            "evidence": evidence,
            "createdAt": now.isoformat().replace("+00:00", "Z"),
        }
        if draft.kind == "PROPOSAL":
            if not draft.summary or not evidence:
                raise AgentPolicyError("AGENT_PROPOSAL_INCOMPLETE")
            if _SENSITIVE.search(draft.summary):
                raise AgentPolicyError("AGENT_SENSITIVE_CANDIDATE_REJECTED")
            semantic = {**common, "proposalType": "SYNTHETIC_SUMMARY", "summary": draft.summary}
        else:
            if not draft.reason_code:
                raise AgentPolicyError("AGENT_NO_ACTION_REASON_REQUIRED")
            semantic = {**common, "reasonCode": draft.reason_code}

        result_hash = _sha256(semantic)
        candidate_id = str(uuid5(
            NAMESPACE_URL,
            f"aden-agent:{job.job_id}:{job.attempt_id}:{job.fencing_token}:{result_hash}",
        ))
        payload = {**semantic, "candidateId": candidate_id, "resultHash": result_hash}
        candidate: Candidate
        if draft.kind == "PROPOSAL":
            candidate = ProposalCandidate.model_validate(payload)
        else:
            candidate = NoActionCandidate.model_validate(payload)
        size = len(candidate.model_dump_json(by_alias=True).encode("utf-8"))
        if size > job.run_spec.max_candidate_bytes:
            raise AgentPolicyError("AGENT_CANDIDATE_BUDGET_EXCEEDED")
        self.validate_candidate(job, candidate)
        return candidate

    def validate_candidate(self, job: AgentJob, candidate: Candidate) -> None:
        if candidate.job_id != job.job_id or candidate.attempt_id != job.attempt_id:
            raise AgentPolicyError("AGENT_CANDIDATE_ATTEMPT_MISMATCH")
        if candidate.fencing_token != job.fencing_token:
            raise AgentPolicyError("AGENT_CANDIDATE_FENCE_MISMATCH")
        facts = {
            (fact.fact_id, fact.source_ref, fact.source_version)
            for fact in job.context_view.facts
        }
        if any((item.fact_id, item.source_ref, item.source_version) not in facts
               for item in candidate.evidence):
            raise AgentPolicyError("AGENT_EVIDENCE_NOT_IN_CONTEXT")


def _sha256(value: object) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        default=lambda item: item.isoformat().replace("+00:00", "Z")
        if isinstance(item, datetime) else str(item),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()
