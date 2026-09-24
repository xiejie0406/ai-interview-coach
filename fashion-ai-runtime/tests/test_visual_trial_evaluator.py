from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path

import pytest
from pydantic import ValidationError

from fashion_ai.evaluation.visual_trial import (
    TIER_ITEM_COUNT,
    TIER_ORDER,
    VisualTrialInput,
    VisualTrialTask,
    evaluate_visual_trial,
    main,
)


def _passing_trial() -> VisualTrialInput:
    submitted = datetime(2026, 9, 13, tzinfo=UTC)
    tasks: list[VisualTrialTask] = []
    for tier in TIER_ORDER:
        for index in range(10):
            deliverable = index < 8
            tasks.append(
                VisualTrialTask(
                    task_id=f"{tier}-{index + 1:02d}",
                    tier=tier,
                    provider_code="candidate-provider",
                    model_version="model-1",
                    config_hash="a" * 64,
                    input_spec_version="input-1",
                    output_spec_version="output-1",
                    review_policy_version="review-1",
                    source_mode="provider",
                    authorized_input=True,
                    attempts=1 if index < 6 else 2,
                    first_round_candidate_count=3,
                    submitted_at=submitted,
                    reviewed_at=submitted + timedelta(seconds=180 + index),
                    adopted_at=(
                        submitted + timedelta(seconds=240 + index)
                        if deliverable
                        else None
                    ),
                    first_round_usable=index < 6,
                    final_deliverable=deliverable,
                    review_decision="adopted" if deliverable else "rejected",
                    adopted_image_count=1 if deliverable else 0,
                    observed_item_count=(
                        TIER_ITEM_COUNT[tier] if deliverable else 0
                    ),
                    actual_cost_cny=Decimal("0.250000"),
                    billing_status="settled",
                    critical_style_error=False,
                    critical_color_error=False,
                    critical_mark_error=False,
                    failure_reason=None if deliverable else "没有业务可用图",
                    reviewer_ref="reviewer-1",
                )
            )
    return VisualTrialInput(
        schema_version="1.0",
        trial_id="trial-001",
        authorization_manifest_sha256="b" * 64,
        fee_cap_cny=Decimal("20.00"),
        expected_task_ids=[task.task_id for task in tasks],
        tasks=tasks,
    )


def test_evaluator_uses_all_40_tasks_and_computes_prd_metrics() -> None:
    report = evaluate_visual_trial(_passing_trial())

    assert report["decision"] == "pass"
    assert report["population"] == {
        "expectedTasks": 40,
        "reportedTasks": 40,
        "failedTasksPreserved": 8,
        "failedTaskIds": [
            f"{tier}-{index:02d}"
            for tier in sorted(TIER_ORDER)
            for index in (9, 10)
        ],
    }
    assert report["metrics"]["firstRoundUsableRatePercent"] == "60.00"
    assert report["metrics"]["finalDeliverableRatePercent"] == "80.00"
    assert report["metrics"]["actualCostCny"] == "10.000000"
    assert report["metrics"]["adoptedImageCount"] == 32
    assert report["metrics"]["costPerAdoptedImageCny"] == "0.312500"
    assert all(value["qualityGatePassed"] for value in report["perTier"].values())
    assert report["stopConditionsTriggered"] == []


def test_trial_rejects_ambiguous_time_and_review_order() -> None:
    task_payload = _passing_trial().tasks[0].model_dump()
    invalid_payloads = (
        {**task_payload, "submitted_at": datetime(2026, 9, 13)},
        {
            **task_payload,
            "adopted_at": task_payload["submitted_at"] + timedelta(seconds=1),
        },
        {**task_payload, "reviewer_ref": "   "},
    )

    for payload in invalid_payloads:
        with pytest.raises(ValidationError):
            VisualTrialTask.model_validate(payload)


def test_evaluator_fails_when_a_tier_drops_below_eight_without_deleting_task() -> None:
    trial = _passing_trial()
    task = trial.tasks[0]
    trial.tasks[0] = task.model_copy(
        update={
            "final_deliverable": False,
            "review_decision": "rejected",
            "adopted_image_count": 0,
            "adopted_at": None,
            "failure_reason": "复核拒绝",
        }
    )

    report = evaluate_visual_trial(trial)

    assert report["decision"] == "fail"
    assert report["population"]["reportedTasks"] == 40
    assert report["perTier"]["one_category"]["finalDeliverableTasks"] == 7
    assert {item["code"] for item in report["stopConditionsTriggered"]} == {
        "category_below_8_of_10"
    }


def test_evaluator_blocks_on_unsettled_billing() -> None:
    trial = _passing_trial()
    trial.tasks[0] = trial.tasks[0].model_copy(update={"billing_status": "unknown"})

    report = evaluate_visual_trial(trial)

    assert report["decision"] == "blocked"
    assert report["billing"]["allSettled"] is False
    assert report["billing"]["unsettledTaskIds"] == ["one_category-01"]
    assert report["stopConditionsTriggered"] == [
        {"code": "unknown_unresolved", "taskIds": ["one_category-01"]}
    ]


def test_evaluator_blocks_when_no_adoption_evidence_exists() -> None:
    trial = _passing_trial()
    trial.tasks = [
        task.model_copy(
            update={
                "review_decision": "deliverable_not_adopted",
                "adopted_image_count": 0,
                "adopted_at": None,
            }
        )
        if task.final_deliverable
        else task
        for task in trial.tasks
    ]

    report = evaluate_visual_trial(trial)

    assert report["decision"] == "blocked"
    assert report["metrics"]["costPerAdoptedImageCny"] is None
    assert report["stopConditionsTriggered"] == [
        {"code": "adoption_evidence_missing"}
    ]


def test_evaluator_stops_on_identity_error_or_four_category_missing_item() -> None:
    trial = _passing_trial()
    trial.tasks[0] = trial.tasks[0].model_copy(update={"critical_color_error": True})
    four_category_index = next(
        index
        for index, task in enumerate(trial.tasks)
        if task.tier == "four_category"
    )
    trial.tasks[four_category_index] = trial.tasks[four_category_index].model_copy(
        update={"observed_item_count": 3}
    )

    report = evaluate_visual_trial(trial)

    assert report["decision"] == "fail"
    assert {item["code"] for item in report["stopConditionsTriggered"]} == {
        "critical_identity_error",
        "four_category_missing_item",
    }


def test_trial_rejects_replaced_task_and_cli_writes_hashed_report(
    tmp_path: Path,
) -> None:
    trial = _passing_trial()
    replaced = trial.model_dump()
    replaced["expected_task_ids"][0] = "unreported-original-task"
    with pytest.raises(ValidationError, match="预登记"):
        VisualTrialInput.model_validate(replaced)

    input_path = tmp_path / "trial.json"
    output_path = tmp_path / "report.json"
    payload = trial.model_dump_json(indent=2)
    input_path.write_text(payload, encoding="utf-8")

    assert main([str(input_path), str(output_path)]) == 0
    report = json.loads(output_path.read_text(encoding="utf-8"))
    assert report["decision"] == "pass"
    assert report["sourceInputSha256"] == hashlib.sha256(
        input_path.read_bytes()
    ).hexdigest()
    assert report["safeguards"]["noProviderCallPerformed"] is True

    input_hash_before = hashlib.sha256(input_path.read_bytes()).hexdigest()
    with pytest.raises(SystemExit):
        main([str(input_path), str(input_path)])
    assert hashlib.sha256(input_path.read_bytes()).hexdigest() == input_hash_before


def test_cli_rejects_incomplete_template_without_writing_report(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    input_path = tmp_path / "incomplete.json"
    output_path = tmp_path / "report.json"
    input_path.write_text(
        json.dumps(
            {
                "schema_version": "1.0",
                "trial_id": "TO_BE_PROVIDED",
                "authorization_manifest_sha256": "0" * 64,
                "fee_cap_cny": "0.00",
                "expected_task_ids": [],
                "tasks": [],
            }
        ),
        encoding="utf-8",
    )

    assert main([str(input_path), str(output_path)]) == 2
    assert "INVALID: visual trial input has" in capsys.readouterr().err
    assert not output_path.exists()
