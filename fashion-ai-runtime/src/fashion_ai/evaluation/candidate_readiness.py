"""交叉核对候选环境、真实视觉试点和业务 UAT 的最终离线门。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from decimal import Decimal
from pathlib import Path
from typing import Any, Literal
from uuid import uuid4

from pydantic import (
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    model_validator,
)

MAX_INPUT_BYTES = 5 * 1024 * 1024
MAX_AUTHORIZATION_MANIFEST_BYTES = 1024 * 1024
MAX_EVIDENCE_BYTES = 50 * 1024 * 1024
SHA256_PATTERN = r"^[a-f0-9]{64}$"
TECHNICAL_GATE_IDS = frozenset(
    {
        "migration_16_tables",
        "capacity_and_concurrency",
        "security_and_authorization",
        "recovery_rpo_rto",
        "worker_and_provider_faults",
        "observability_and_retention",
        "desktop_compatibility",
    }
)
UAT_SCENARIO_IDS = frozenset(
    {"UAT-01", "UAT-02", "UAT-03", "UAT-04", "UAT-05", "UAT-06"}
)
TIER_IDS = frozenset(
    {"one_category", "two_category", "three_category", "four_category"}
)
SECRET_PATTERN = re.compile(
    r'(?i)"(?:api[_-]?key|secret(?:value)?|access[_-]?token|refresh[_-]?token|'
    r'password|credential|client[_-]?secret|private[_-]?key)"\s*:'
)
SECRET_BYTES_PATTERN = re.compile(
    rb'(?i)"(?:api[_-]?key|secret(?:value)?|access[_-]?token|refresh[_-]?token|'
    rb'password|credential|client[_-]?secret|private[_-]?key)"\s*:'
)
PRIVATE_KEY_BYTES_PATTERN = re.compile(
    rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
)


class CandidateTrialPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, populate_by_name=True)

    fee_cap_cny: Decimal = Field(alias="feeCapCny", ge=0, decimal_places=2)
    minimum_deliverable_tasks_per_category: Literal[8] = Field(
        alias="minimumDeliverableTasksPerCategory"
    )


class CandidatePreflightSafeguards(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, populate_by_name=True)

    no_network_performed: Literal[True] = Field(alias="noNetworkPerformed")
    no_database_write_performed: Literal[True] = Field(
        alias="noDatabaseWritePerformed"
    )
    no_provider_call_performed: Literal[True] = Field(
        alias="noProviderCallPerformed"
    )
    secret_values_accepted: Literal[False] = Field(alias="secretValuesAccepted")
    release_excluded: Literal[True] = Field(alias="releaseExcluded")
    expected_fashion_table_count: Literal[16] = Field(
        alias="expectedFashionTableCount"
    )


class CandidatePreflightReport(BaseModel):
    model_config = ConfigDict(extra="allow", strict=True, populate_by_name=True)

    schema_version: Literal["1.0"] = Field(alias="schemaVersion")
    source_manifest_sha256: str = Field(
        alias="sourceManifestSha256", pattern=SHA256_PATTERN
    )
    ready: bool
    gaps: list[str]
    trial_policy: CandidateTrialPolicy = Field(alias="trialPolicy")
    safeguards: CandidatePreflightSafeguards


class VisualPopulation(BaseModel):
    model_config = ConfigDict(extra="allow", strict=True, populate_by_name=True)

    expected_tasks: int = Field(alias="expectedTasks", ge=0)
    reported_tasks: int = Field(alias="reportedTasks", ge=0)


class VisualTierResult(BaseModel):
    model_config = ConfigDict(extra="allow", strict=True, populate_by_name=True)

    submitted_tasks: int = Field(alias="submittedTasks", ge=0)
    final_deliverable_tasks: int = Field(alias="finalDeliverableTasks", ge=0)
    quality_gate_passed: bool = Field(alias="qualityGatePassed")


class VisualBillingResult(BaseModel):
    model_config = ConfigDict(extra="allow", strict=True, populate_by_name=True)

    all_settled: bool = Field(alias="allSettled")


class VisualMetrics(BaseModel):
    model_config = ConfigDict(extra="allow", strict=True, populate_by_name=True)

    fee_cap_cny: Decimal = Field(alias="feeCapCny", gt=0, decimal_places=6)


class VisualSafeguards(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, populate_by_name=True)

    all_expected_tasks_present: Literal[True] = Field(
        alias="allExpectedTasksPresent"
    )
    failures_remain_in_denominator: Literal[True] = Field(
        alias="failuresRemainInDenominator"
    )
    no_network_performed: Literal[True] = Field(alias="noNetworkPerformed")
    no_provider_call_performed: Literal[True] = Field(
        alias="noProviderCallPerformed"
    )
    release_decision_included: Literal[False] = Field(
        alias="releaseDecisionIncluded"
    )


class VisualTrialReport(BaseModel):
    model_config = ConfigDict(extra="allow", strict=True, populate_by_name=True)

    schema_version: Literal["1.0"] = Field(alias="schemaVersion")
    authorization_manifest_sha256: str = Field(
        alias="authorizationManifestSha256", pattern=SHA256_PATTERN
    )
    decision: Literal["pass", "fail", "blocked"]
    population: VisualPopulation
    per_tier: dict[str, VisualTierResult] = Field(alias="perTier")
    metrics: VisualMetrics
    billing: VisualBillingResult
    safeguards: VisualSafeguards
    stop_conditions_triggered: list[dict[str, Any]] = Field(
        alias="stopConditionsTriggered"
    )


class EvidenceGate(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    gate_id: str = Field(min_length=1, max_length=64)
    result: Literal["pass", "fail", "blocked", "not_run"]
    evidence_ref: str | None = Field(default=None, min_length=1, max_length=500)
    evidence_file: str | None = Field(default=None, min_length=1, max_length=500)
    evidence_sha256: str | None = Field(default=None, pattern=SHA256_PATTERN)
    executed_by: str | None = Field(default=None, min_length=1, max_length=100)
    executed_at: AwareDatetime | None = None

    @model_validator(mode="after")
    def require_evidence_for_executed_gate(self) -> EvidenceGate:
        if self.result != "not_run":
            required_values = (
                self.evidence_ref,
                self.evidence_file,
                self.evidence_sha256,
                self.executed_by,
                self.executed_at,
            )
            if any(value is None for value in required_values) or any(
                isinstance(value, str) and not value.strip()
                for value in required_values
            ):
                raise ValueError("已执行 gate 必须提供完整证据引用、摘要、人员和时间")
        return self


class UatDecision(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    decision: Literal["accepted", "accepted_with_conditions", "rejected", "not_run"]
    decided_by: str | None = Field(default=None, min_length=1, max_length=100)
    business_role: str | None = Field(default=None, min_length=1, max_length=100)
    decided_at: AwareDatetime | None = None
    conditions: list[str] = Field(default_factory=list, max_length=50)

    @model_validator(mode="after")
    def require_business_decider(self) -> UatDecision:
        if self.decision != "not_run":
            required_values = (self.decided_by, self.business_role, self.decided_at)
            if any(value is None for value in required_values) or any(
                isinstance(value, str) and not value.strip()
                for value in required_values
            ):
                raise ValueError("UAT 决定必须记录业务决定人、角色和时间")
        if any(not condition.strip() for condition in self.conditions):
            raise ValueError("conditions 不得包含空白条件")
        if self.decision == "accepted_with_conditions" and not self.conditions:
            raise ValueError("附条件接受必须记录 conditions")
        if self.decision != "accepted_with_conditions" and self.conditions:
            raise ValueError("只有附条件接受可以记录 conditions")
        return self


class CandidateReadinessEvidence(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    schema_version: Literal["1.0"]
    authorization_manifest_sha256: str = Field(pattern=SHA256_PATTERN)
    technical_gates: list[EvidenceGate]
    uat_scenarios: list[EvidenceGate]
    uat_decision: UatDecision
    release_authorized: Literal[False]

    @model_validator(mode="after")
    def validate_complete_gate_sets(self) -> CandidateReadinessEvidence:
        technical_ids = [gate.gate_id for gate in self.technical_gates]
        if len(technical_ids) != len(set(technical_ids)) or set(
            technical_ids
        ) != set(TECHNICAL_GATE_IDS):
            raise ValueError("technical_gates 必须精确覆盖七个候选环境 gate")
        uat_ids = [gate.gate_id for gate in self.uat_scenarios]
        if len(uat_ids) != len(set(uat_ids)) or set(uat_ids) != set(
            UAT_SCENARIO_IDS
        ):
            raise ValueError("uat_scenarios 必须精确覆盖 UAT-01～UAT-06")
        return self


def evaluate_candidate_readiness(
    preflight: CandidatePreflightReport,
    visual: VisualTrialReport,
    evidence: CandidateReadinessEvidence,
    *,
    evidence_files_verified: bool = False,
    authorization_manifest_verified: bool = False,
) -> dict[str, Any]:
    """返回上线就绪判定；无论结果如何都不包含发布授权。"""

    reasons: list[str] = []
    if not authorization_manifest_verified:
        reasons.append("authorization_manifest_not_verified")
    if not evidence_files_verified:
        reasons.append("technical_evidence_files_not_verified")
    if not preflight.ready or preflight.gaps:
        reasons.append("candidate_preflight_not_ready")
    hashes = {
        preflight.source_manifest_sha256,
        visual.authorization_manifest_sha256,
        evidence.authorization_manifest_sha256,
    }
    if len(hashes) != 1:
        reasons.append("authorization_manifest_hash_mismatch")
    if visual.metrics.fee_cap_cny != preflight.trial_policy.fee_cap_cny:
        reasons.append("visual_trial_fee_cap_mismatch")
    if visual.decision != "pass":
        reasons.append(f"visual_trial_{visual.decision}")
    if (
        visual.population.expected_tasks != 40
        or visual.population.reported_tasks != 40
    ):
        reasons.append("visual_trial_population_not_40")
    if set(visual.per_tier) != set(TIER_IDS) or any(
        tier.submitted_tasks != 10
        or tier.final_deliverable_tasks < 8
        or not tier.quality_gate_passed
        for tier in visual.per_tier.values()
    ):
        reasons.append("visual_trial_tier_gate_failed")
    if not visual.billing.all_settled:
        reasons.append("visual_trial_billing_unsettled")
    if visual.stop_conditions_triggered:
        reasons.append("visual_trial_stop_condition_triggered")
    for gate in evidence.technical_gates:
        if gate.result != "pass":
            reasons.append(f"technical_gate_{gate.gate_id}_{gate.result}")
    for gate in evidence.uat_scenarios:
        if gate.result != "pass":
            reasons.append(f"uat_scenario_{gate.gate_id}_{gate.result}")
    if evidence.uat_decision.decision != "accepted":
        reasons.append(f"uat_decision_{evidence.uat_decision.decision}")
    return {
        "schemaVersion": "1.0",
        "releaseReady": not reasons,
        "reasons": reasons,
        "authorizationManifestSha256": preflight.source_manifest_sha256,
        "technicalGateCount": len(evidence.technical_gates),
        "uatScenarioCount": len(evidence.uat_scenarios),
        "visualTrialDecision": visual.decision,
        "uatDecision": evidence.uat_decision.decision,
        "releaseAuthorized": False,
        "releaseStatus": "NotReleased",
        "safeguards": {
            "technicalEvidenceFilesVerified": evidence_files_verified,
            "authorizationManifestVerified": authorization_manifest_verified,
            "noNetworkPerformed": True,
            "noDatabaseWritePerformed": True,
            "noProviderCallPerformed": True,
            "userOrBusinessUatRequired": True,
            "releaseRequiresSeparateAuthorization": True,
        },
    }


def _read_json(path: Path, model: type[BaseModel]) -> tuple[BaseModel, str]:
    payload = path.read_bytes()
    if len(payload) > MAX_INPUT_BYTES:
        raise ValueError("单个候选证据文件不得超过 5 MiB")
    raw = payload.decode("utf-8")
    if SECRET_PATTERN.search(raw) or re.search(
        r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", raw
    ):
        raise ValueError("候选证据文件包含疑似 Secret 或私钥字段")
    return model.model_validate_json(raw), hashlib.sha256(payload).hexdigest()


def _read_authorization_manifest(path: Path) -> str:
    payload = path.read_bytes()
    if len(payload) > MAX_AUTHORIZATION_MANIFEST_BYTES:
        raise ValueError("候选授权清单不得超过 1 MiB")
    raw = payload.decode("utf-8")
    if SECRET_PATTERN.search(raw) or re.search(
        r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", raw
    ):
        raise ValueError("候选授权清单包含疑似 Secret 或私钥字段")
    parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError("候选授权清单必须是 JSON object")
    return hashlib.sha256(payload).hexdigest()


def verify_evidence_files(
    evidence: CandidateReadinessEvidence, evidence_manifest_path: Path
) -> None:
    bundle_root = evidence_manifest_path.parent.resolve()
    for gate in [*evidence.technical_gates, *evidence.uat_scenarios]:
        if gate.result == "not_run":
            continue
        assert gate.evidence_file is not None
        assert gate.evidence_sha256 is not None
        relative = Path(gate.evidence_file)
        if relative.is_absolute():
            raise ValueError(f"gate {gate.gate_id} 的 evidence_file 必须是相对路径")
        candidate = (bundle_root / relative).resolve()
        if not candidate.is_relative_to(bundle_root) or not candidate.is_file():
            raise ValueError(f"gate {gate.gate_id} 的证据文件不在证据包内或不存在")
        if candidate.stat().st_size > MAX_EVIDENCE_BYTES:
            raise ValueError(f"gate {gate.gate_id} 的单个证据文件超过 50 MiB")
        payload = candidate.read_bytes()
        if len(payload) > MAX_EVIDENCE_BYTES:
            raise ValueError(f"gate {gate.gate_id} 的单个证据文件超过 50 MiB")
        if SECRET_BYTES_PATTERN.search(payload) or PRIVATE_KEY_BYTES_PATTERN.search(
            payload
        ):
            raise ValueError(f"gate {gate.gate_id} 的证据文件包含疑似 Secret 或私钥")
        actual_hash = hashlib.sha256(payload).hexdigest()
        if actual_hash != gate.evidence_sha256:
            raise ValueError(f"gate {gate.gate_id} 的证据 SHA-256 不匹配")


def _write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid4().hex}.tmp")
    try:
        temporary.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="离线交叉核对生产候选上线就绪证据")
    parser.add_argument("authorization", type=Path, help="原始候选授权清单 JSON")
    parser.add_argument("preflight", type=Path, help="候选输入预检报告 JSON")
    parser.add_argument("visual", type=Path, help="真实视觉试点聚合报告 JSON")
    parser.add_argument("evidence", type=Path, help="技术与 UAT 证据清单 JSON")
    parser.add_argument("output", type=Path, help="最终上线就绪报告 JSON")
    args = parser.parse_args(argv)
    try:
        authorization_path = args.authorization.resolve(strict=True)
        preflight_path = args.preflight.resolve(strict=True)
        visual_path = args.visual.resolve(strict=True)
        evidence_path = args.evidence.resolve(strict=True)
        output_path = args.output.resolve()
        if output_path in {
            authorization_path,
            preflight_path,
            visual_path,
            evidence_path,
        }:
            parser.error("输出文件不能覆盖任何输入证据")
        authorization_hash = _read_authorization_manifest(authorization_path)
        preflight, preflight_hash = _read_json(
            preflight_path, CandidatePreflightReport
        )
        visual, visual_hash = _read_json(
            visual_path, VisualTrialReport
        )
        evidence, evidence_hash = _read_json(evidence_path, CandidateReadinessEvidence)
    except ValidationError as exception:
        print(
            "INVALID: candidate evidence has "
            f"{exception.error_count()} validation error(s)",
            file=sys.stderr,
        )
        return 2
    except (OSError, UnicodeError, ValueError) as exception:
        print(f"INVALID: {type(exception).__name__}", file=sys.stderr)
        return 2
    assert isinstance(preflight, CandidatePreflightReport)
    assert isinstance(visual, VisualTrialReport)
    assert isinstance(evidence, CandidateReadinessEvidence)
    try:
        verify_evidence_files(evidence, evidence_path)
    except (OSError, ValueError) as exception:
        print(f"INVALID: {type(exception).__name__}", file=sys.stderr)
        return 2
    report = evaluate_candidate_readiness(
        preflight,
        visual,
        evidence,
        evidence_files_verified=True,
        authorization_manifest_verified=(
            authorization_hash == preflight.source_manifest_sha256
        ),
    )
    report["sourceEvidenceSha256"] = {
        "authorizationManifest": authorization_hash,
        "preflight": preflight_hash,
        "visual": visual_hash,
        "evidence": evidence_hash,
    }
    _write_report(output_path, report)
    state = "PASS" if report["releaseReady"] else "BLOCKED"
    print(f"{state}: candidate readiness evaluated; report={output_path}")
    return 0 if report["releaseReady"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
