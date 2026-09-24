"""按 PRD 固定口径离线汇总 40 个真实视觉试点任务。"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from collections import Counter
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path
from statistics import median
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

Tier = Literal[
    "one_category",
    "two_category",
    "three_category",
    "four_category",
]
BillingStatus = Literal["reserved", "unknown", "settled"]
ReviewDecision = Literal["adopted", "deliverable_not_adopted", "rejected", "failed"]

TIER_ORDER: tuple[Tier, ...] = (
    "one_category",
    "two_category",
    "three_category",
    "four_category",
)
TIER_ITEM_COUNT: dict[Tier, int] = {
    "one_category": 1,
    "two_category": 2,
    "three_category": 3,
    "four_category": 4,
}
MONEY_QUANTUM = Decimal("0.000001")
RATE_QUANTUM = Decimal("0.01")
MAX_INPUT_BYTES = 5 * 1024 * 1024
SECRET_PATTERN = re.compile(
    r'(?i)"(?:api[_-]?key|secret(?:value)?|access[_-]?token|refresh[_-]?token|'
    r'password|credential|client[_-]?secret|private[_-]?key)"\s*:'
)


class VisualTrialTask(BaseModel):
    """一条预登记视觉任务的脱敏业务审核结果。"""

    model_config = ConfigDict(extra="forbid", strict=True)

    task_id: str = Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")
    tier: Tier
    provider_code: str = Field(min_length=1, max_length=64)
    model_version: str = Field(min_length=1, max_length=128)
    config_hash: str = Field(pattern=r"^[a-f0-9]{64}$")
    input_spec_version: str = Field(min_length=1, max_length=64)
    output_spec_version: str = Field(min_length=1, max_length=64)
    review_policy_version: str = Field(min_length=1, max_length=64)
    source_mode: Literal["provider"]
    authorized_input: bool
    attempts: int = Field(ge=1, le=2)
    first_round_candidate_count: Literal[3]
    submitted_at: AwareDatetime
    reviewed_at: AwareDatetime
    adopted_at: AwareDatetime | None = None
    first_round_usable: bool
    final_deliverable: bool
    review_decision: ReviewDecision
    adopted_image_count: int = Field(ge=0, le=3)
    observed_item_count: int = Field(ge=0, le=4)
    actual_cost_cny: Decimal = Field(ge=0, decimal_places=6)
    billing_status: BillingStatus
    critical_style_error: bool
    critical_color_error: bool
    critical_mark_error: bool
    failure_reason: str | None = Field(default=None, max_length=500)
    reviewer_ref: str = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def validate_timeline_and_decision(self) -> VisualTrialTask:
        text_values = (
            self.provider_code,
            self.model_version,
            self.input_spec_version,
            self.output_spec_version,
            self.review_policy_version,
            self.reviewer_ref,
        )
        if any(not value.strip() for value in text_values):
            raise ValueError("Provider、配置版本和复核人引用不得为空白")
        if self.reviewed_at < self.submitted_at:
            raise ValueError("reviewed_at 不能早于 submitted_at")
        if self.adopted_at is not None and self.adopted_at < self.reviewed_at:
            raise ValueError("adopted_at 不能早于 reviewed_at")
        adopted = self.review_decision == "adopted"
        if adopted != (self.adopted_image_count > 0):
            raise ValueError("adopted 决定与 adopted_image_count 必须一致")
        if adopted != (self.adopted_at is not None):
            raise ValueError("adopted 决定与 adopted_at 必须一致")
        if self.final_deliverable != (
            self.review_decision in {"adopted", "deliverable_not_adopted"}
        ):
            raise ValueError("final_deliverable 与 review_decision 必须一致")
        if not self.final_deliverable and (
            self.failure_reason is None or not self.failure_reason.strip()
        ):
            raise ValueError("不可交付任务必须保留 failure_reason")
        return self


class VisualTrialInput(BaseModel):
    """固定 40 个任务的完整结果集；expected_task_ids 防止替换失败样本。"""

    model_config = ConfigDict(extra="forbid", strict=True)

    schema_version: Literal["1.0"]
    trial_id: str = Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")
    authorization_manifest_sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    fee_cap_cny: Decimal = Field(gt=0, decimal_places=2)
    expected_task_ids: list[str] = Field(min_length=40, max_length=40)
    tasks: list[VisualTrialTask] = Field(min_length=40, max_length=40)

    @model_validator(mode="after")
    def validate_closed_population_and_consistency(self) -> VisualTrialInput:
        if len(set(self.expected_task_ids)) != 40:
            raise ValueError("expected_task_ids 必须包含 40 个唯一任务")
        actual_ids = [task.task_id for task in self.tasks]
        if len(set(actual_ids)) != 40:
            raise ValueError("tasks 必须包含 40 个唯一任务")
        if set(actual_ids) != set(self.expected_task_ids):
            raise ValueError("tasks 必须与预登记 expected_task_ids 完全一致")
        tier_counts = Counter(task.tier for task in self.tasks)
        if any(tier_counts[tier] != 10 for tier in TIER_ORDER):
            raise ValueError("1/2/3/4 品类组必须各有 10 个任务")
        for field_name in (
            "provider_code",
            "model_version",
            "config_hash",
            "input_spec_version",
            "output_spec_version",
            "review_policy_version",
        ):
            if len({getattr(task, field_name) for task in self.tasks}) != 1:
                raise ValueError(f"全部任务必须使用一致的 {field_name}")
        return self


def _rate(numerator: int, denominator: int) -> str:
    value = (Decimal(numerator) * 100 / Decimal(denominator)).quantize(
        RATE_QUANTUM, rounding=ROUND_HALF_UP
    )
    return f"{value:.2f}"


def _money(value: Decimal) -> str:
    return f"{value.quantize(MONEY_QUANTUM, rounding=ROUND_HALF_UP):.6f}"


def _percentile_95(values: list[int]) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def evaluate_visual_trial(trial: VisualTrialInput) -> dict[str, Any]:
    """返回脱敏聚合结果；不丢弃失败任务，不访问网络或 Provider。"""

    per_tier: dict[str, dict[str, Any]] = {}
    first_round_total = 0
    final_total = 0
    adopted_images = 0
    total_cost = Decimal("0")
    adoption_durations: list[int] = []
    failed_task_ids: list[str] = []
    unsettled_task_ids: list[str] = []
    unauthorized_task_ids: list[str] = []
    critical_identity_task_ids: list[str] = []
    missing_item_task_ids: dict[str, list[str]] = {
        "required_item_missing": [],
        "four_category_missing_item": [],
    }

    for tier in TIER_ORDER:
        tasks = [task for task in trial.tasks if task.tier == tier]
        first_round = sum(task.first_round_usable for task in tasks)
        deliverable = sum(task.final_deliverable for task in tasks)
        first_round_total += first_round
        final_total += deliverable
        per_tier[tier] = {
            "submittedTasks": len(tasks),
            "firstRoundUsableTasks": first_round,
            "firstRoundUsableRatePercent": _rate(first_round, len(tasks)),
            "finalDeliverableTasks": deliverable,
            "finalDeliverableRatePercent": _rate(deliverable, len(tasks)),
            "qualityGatePassed": deliverable >= 8,
        }

    for task in trial.tasks:
        adopted_images += task.adopted_image_count
        total_cost += task.actual_cost_cny
        if not task.final_deliverable:
            failed_task_ids.append(task.task_id)
        if task.billing_status != "settled":
            unsettled_task_ids.append(task.task_id)
        if not task.authorized_input:
            unauthorized_task_ids.append(task.task_id)
        if task.final_deliverable and (
            task.critical_style_error
            or task.critical_color_error
            or task.critical_mark_error
        ):
            critical_identity_task_ids.append(task.task_id)
        if (
            task.final_deliverable
            and task.observed_item_count != TIER_ITEM_COUNT[task.tier]
        ):
            code = (
                "four_category_missing_item"
                if task.tier == "four_category"
                else "required_item_missing"
            )
            missing_item_task_ids[code].append(task.task_id)
        if task.adopted_at is not None:
            adoption_durations.append(
                int((task.adopted_at - task.submitted_at).total_seconds())
            )

    stop_conditions: list[dict[str, Any]] = []
    for tier, metrics in per_tier.items():
        if not metrics["qualityGatePassed"]:
            stop_conditions.append(
                {"code": "category_below_8_of_10", "tier": tier}
            )
    if total_cost > trial.fee_cap_cny:
        stop_conditions.append(
            {
                "code": "fee_cap_reached",
                "actualCostCny": _money(total_cost),
                "feeCapCny": _money(trial.fee_cap_cny),
            }
        )
    if unauthorized_task_ids:
        stop_conditions.append(
            {"code": "security_violation", "taskIds": unauthorized_task_ids}
        )
    if critical_identity_task_ids:
        stop_conditions.append(
            {
                "code": "critical_identity_error",
                "taskIds": critical_identity_task_ids,
            }
        )
    for code, task_ids in missing_item_task_ids.items():
        if task_ids:
            stop_conditions.append({"code": code, "taskIds": task_ids})
    if unsettled_task_ids:
        stop_conditions.append(
            {"code": "unknown_unresolved", "taskIds": unsettled_task_ids}
        )
    if adopted_images == 0:
        stop_conditions.append({"code": "adoption_evidence_missing"})

    blocking_codes = {"unknown_unresolved", "adoption_evidence_missing"}
    failure_conditions = [
        item for item in stop_conditions if item["code"] not in blocking_codes
    ]
    decision = (
        "fail"
        if failure_conditions
        else "blocked"
        if stop_conditions
        else "pass"
    )
    cost_per_adopted = total_cost / adopted_images if adopted_images else None
    return {
        "schemaVersion": "1.0",
        "trialId": trial.trial_id,
        "authorizationManifestSha256": trial.authorization_manifest_sha256,
        "decision": decision,
        "configuration": {
            "providerCode": trial.tasks[0].provider_code,
            "modelVersion": trial.tasks[0].model_version,
            "configHash": trial.tasks[0].config_hash,
            "inputSpecVersion": trial.tasks[0].input_spec_version,
            "outputSpecVersion": trial.tasks[0].output_spec_version,
            "reviewPolicyVersion": trial.tasks[0].review_policy_version,
        },
        "population": {
            "expectedTasks": len(trial.expected_task_ids),
            "reportedTasks": len(trial.tasks),
            "failedTasksPreserved": len(failed_task_ids),
            "failedTaskIds": sorted(failed_task_ids),
        },
        "metrics": {
            "firstRoundUsableTasks": first_round_total,
            "firstRoundUsableRatePercent": _rate(first_round_total, 40),
            "finalDeliverableTasks": final_total,
            "finalDeliverableRatePercent": _rate(final_total, 40),
            "actualCostCny": _money(total_cost),
            "feeCapCny": _money(trial.fee_cap_cny),
            "adoptedImageCount": adopted_images,
            "costPerAdoptedImageCny": (
                _money(cost_per_adopted) if cost_per_adopted is not None else None
            ),
            "adoptionDurationSeconds": {
                "sampleCount": len(adoption_durations),
                "total": sum(adoption_durations),
                "median": (
                    median(adoption_durations) if adoption_durations else None
                ),
                "p95": _percentile_95(adoption_durations),
            },
        },
        "perTier": per_tier,
        "billing": {
            "allSettled": not unsettled_task_ids,
            "unsettledTaskIds": sorted(unsettled_task_ids),
        },
        "stopConditionsTriggered": stop_conditions,
        "safeguards": {
            "allExpectedTasksPresent": True,
            "failuresRemainInDenominator": True,
            "noNetworkPerformed": True,
            "noProviderCallPerformed": True,
            "releaseDecisionIncluded": False,
        },
    }


def _read_input(path: Path) -> tuple[VisualTrialInput, str]:
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise ValueError("视觉试点结果不得超过 5 MiB")
    payload = path.read_bytes()
    raw = payload.decode("utf-8")
    if SECRET_PATTERN.search(raw) or re.search(
        r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", raw
    ):
        raise ValueError("结果文件包含疑似 Secret 或私钥字段")
    input_hash = hashlib.sha256(payload).hexdigest()
    return VisualTrialInput.model_validate_json(raw), input_hash


def _write_report(path: Path, report: dict[str, Any], input_hash: str) -> None:
    enriched = {**report, "sourceInputSha256": input_hash}
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid4().hex}.tmp")
    try:
        temporary.write_text(
            json.dumps(enriched, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="离线汇总 40 个真实视觉试点任务")
    parser.add_argument("input", type=Path, help="脱敏视觉试点结果 JSON")
    parser.add_argument("output", type=Path, help="脱敏聚合报告 JSON")
    args = parser.parse_args(argv)
    try:
        input_path = args.input.resolve(strict=True)
        trial, input_hash = _read_input(input_path)
    except ValidationError as exception:
        print(
            "INVALID: visual trial input has "
            f"{exception.error_count()} validation error(s)",
            file=sys.stderr,
        )
        return 2
    except (OSError, UnicodeError, ValueError) as exception:
        print(f"INVALID: {type(exception).__name__}", file=sys.stderr)
        return 2
    output_path = args.output.resolve()
    if input_path == output_path:
        parser.error("输出文件不能覆盖输入文件")
    report = evaluate_visual_trial(trial)
    _write_report(output_path, report, input_hash)
    print(
        f"{report['decision'].upper()}: visual trial evaluated; "
        f"tasks={len(trial.tasks)} report={output_path}"
    )
    return 0 if report["decision"] == "pass" else 2


if __name__ == "__main__":
    raise SystemExit(main())
