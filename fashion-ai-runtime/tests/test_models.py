from copy import deepcopy
from datetime import UTC, datetime, timedelta
from typing import cast
from uuid import uuid4

import pytest
from pydantic import ValidationError

from fashion_ai.models import (
    BudgetConstraint,
    RequirementAnalysisRequest,
    RequirementAnalysisResponse,
    RequirementAnalysisResult,
)


def valid_request_model_data() -> dict[str, object]:
    return {
        "version": "1.0",
        "request_id": str(uuid4()),
        "run_id": "42",
        "idempotency_key": f"analysis:{uuid4()}",
        "deadline_at": (datetime.now(UTC) + timedelta(minutes=5)).isoformat(),
        "input": {
            "customer_ref": "1",
            "source_text": "为一百名员工准备秋季团建服装，共一百套，偏休闲风格。",
            "known_requirements": {
                "scheme_name": "森禾秋季团建服装方案",
                "audience": "企业员工",
                "people_count": 100,
                "set_count": 100,
                "selection_mode": "progressive",
                "category_tiers": [
                    {
                        "category_count": 1,
                        "candidate_count": 2,
                        "slots": [
                            {"slot_index": 1, "category": "上衣", "required": True}
                        ],
                    }
                ],
            },
        },
    }


def valid_result_data() -> dict[str, object]:
    return {
        "fact_scope": "requirements_only",
        "human_confirmation_required": True,
        "draft": {
            "status": "pending_human_confirmation",
            "scheme_name": "森禾秋季团建服装方案",
            "audience": "企业员工",
            "scene": "企业团建",
            "season": "秋季",
            "style": "休闲",
            "preferred_colors": ["米杏"],
            "exclusions": ["藏蓝"],
            "people_count": 100,
            "set_count": 100,
            "delivery_date": "2026-10-15",
            "size_requirements": [
                {"size_label": "M", "quantity": 40},
                {"size_label": "L", "quantity": 60},
            ],
            "selection_mode": "progressive",
            "category_tiers": [
                {
                    "category_count": 1,
                    "candidate_count": 2,
                    "slots": [{"slot_index": 1, "category": "上衣", "required": True}],
                },
                {
                    "category_count": 2,
                    "candidate_count": 2,
                    "slots": [
                        {"slot_index": 1, "category": "上衣", "required": True},
                        {"slot_index": 2, "category": "裤子", "required": True},
                    ],
                },
            ],
            "budget_constraints": [
                {
                    "basis": "total",
                    "currency": "CNY",
                    "minimum_minor": 2_000_000,
                    "maximum_minor": 3_000_000,
                    "includes_fees": True,
                },
                {
                    "basis": "per_set",
                    "currency": "CNY",
                    "minimum_minor": 20_000,
                    "maximum_minor": 30_000,
                    "includes_fees": True,
                },
            ],
        },
        "unresolved_questions": [],
    }


def test_request_accepts_bigint_customer_ref_and_stable_run_id() -> None:
    parsed = RequirementAnalysisRequest.model_validate(valid_request_model_data())

    assert parsed.input.customer_ref == "1"
    assert parsed.run_id == "42"


def test_request_model_rejects_short_idempotency_key() -> None:
    payload = valid_request_model_data()
    payload["idempotency_key"] = "too-short"

    with pytest.raises(ValidationError):
        RequirementAnalysisRequest.model_validate(payload)


def test_request_model_rejects_naive_deadline() -> None:
    payload = valid_request_model_data()
    payload["deadline_at"] = "2030-01-01T00:00:00"

    with pytest.raises(ValidationError):
        RequirementAnalysisRequest.model_validate(payload)


@pytest.mark.parametrize("deadline", [1_800_000_000, "1800000000"])
def test_request_model_rejects_numeric_deadline(deadline: object) -> None:
    payload = valid_request_model_data()
    payload["deadline_at"] = deadline

    with pytest.raises(ValidationError):
        RequirementAnalysisRequest.model_validate(payload)


@pytest.mark.parametrize("forbidden_field", ["customer", "customer_name", "contact"])
def test_request_accepts_only_stable_customer_ref(forbidden_field: str) -> None:
    payload = valid_request_model_data()
    payload["input"][forbidden_field] = "不得传入的客户资料"  # type: ignore[index]

    with pytest.raises(ValidationError):
        RequirementAnalysisRequest.model_validate(payload)


def test_category_tier_rejects_non_contiguous_slots() -> None:
    payload = valid_request_model_data()
    known = payload["input"]["known_requirements"]  # type: ignore[index]
    known["category_tiers"] = [  # type: ignore[index]
        {
            "category_count": 2,
            "candidate_count": 2,
            "slots": [
                {"slot_index": 1, "category": "上衣", "required": True},
                {"slot_index": 3, "category": "鞋", "required": True},
            ],
        }
    ]

    with pytest.raises(ValidationError):
        RequirementAnalysisRequest.model_validate(payload)


def test_category_tiers_require_unique_ascending_counts() -> None:
    payload = valid_request_model_data()
    known = payload["input"]["known_requirements"]  # type: ignore[index]
    known["category_tiers"] = [  # type: ignore[index]
        {
            "category_count": 2,
            "candidate_count": 2,
            "slots": [
                {"slot_index": 1, "category": "上衣"},
                {"slot_index": 2, "category": "裤子"},
            ],
        },
        {
            "category_count": 1,
            "candidate_count": 2,
            "slots": [{"slot_index": 1, "category": "上衣"}],
        },
    ]

    with pytest.raises(ValidationError):
        RequirementAnalysisRequest.model_validate(payload)


def test_budget_requires_maximum_not_less_than_minimum() -> None:
    with pytest.raises(ValidationError):
        BudgetConstraint(
            basis="total",
            currency="CNY",
            minimum_minor=3_000_000,
            maximum_minor=2_000_000,
            includes_fees=True,
        )


@pytest.mark.parametrize("currency", ["USD", "EUR"])
def test_first_release_budget_accepts_only_cny(currency: str) -> None:
    with pytest.raises(ValidationError):
        BudgetConstraint.model_validate(
            {
                "basis": "total",
                "currency": currency,
                "minimum_minor": 1,
                "maximum_minor": 2,
                "includes_fees": True,
            }
        )


def test_budget_rejects_zero_minimum() -> None:
    with pytest.raises(ValidationError):
        BudgetConstraint(
            basis="total",
            currency="CNY",
            minimum_minor=0,
            maximum_minor=1,
            includes_fees=True,
        )


def test_budget_constraints_reject_duplicate_basis() -> None:
    payload = valid_result_data()
    draft = cast(dict[str, object], payload["draft"])
    budgets = cast(list[dict[str, object]], draft["budget_constraints"])
    budgets.append(deepcopy(budgets[0]))

    with pytest.raises(ValidationError):
        RequirementAnalysisResult.model_validate(payload)


@pytest.mark.parametrize(
    "forbidden_field, forbidden_value",
    [
        ("product_candidates", ["product-001"]),
        ("sku", "SKU-001"),
        ("price_minor", 10_000),
        ("inventory", 100),
    ],
)
def test_structured_draft_rejects_product_facts(
    forbidden_field: str, forbidden_value: object
) -> None:
    payload = valid_result_data()
    payload["draft"][forbidden_field] = forbidden_value  # type: ignore[index]

    with pytest.raises(ValidationError):
        RequirementAnalysisResult.model_validate(payload)


@pytest.mark.parametrize(
    "customer_field",
    ["customer", "customer_ref", "customer_id", "customer_name"],
)
def test_response_draft_cannot_carry_customer_fields(customer_field: str) -> None:
    result = valid_result_data()
    result["draft"][customer_field] = "untrusted-customer"  # type: ignore[index]
    response = {
        "version": "1.0",
        "request_id": str(uuid4()),
        "run_id": "42",
        "result": result,
    }

    with pytest.raises(ValidationError):
        RequirementAnalysisResponse.model_validate(response)


def test_category_tier_candidate_count_is_limited_to_three() -> None:
    payload = valid_result_data()
    payload["draft"]["category_tiers"][0]["candidate_count"] = 4  # type: ignore[index]

    with pytest.raises(ValidationError):
        RequirementAnalysisResult.model_validate(payload)


def test_incomplete_draft_can_ask_for_blocking_fields_without_guessing() -> None:
    payload = valid_result_data()
    payload["draft"]["scheme_name"] = None  # type: ignore[index]
    payload["draft"]["set_count"] = None  # type: ignore[index]
    payload["draft"]["selection_mode"] = None  # type: ignore[index]
    payload["draft"]["category_tiers"] = []  # type: ignore[index]
    payload["unresolved_questions"] = ["请确认方案名称、采购套数和品类档位"]

    parsed = RequirementAnalysisResult.model_validate(payload)

    assert parsed.draft.selection_mode is None
    assert parsed.draft.category_tiers == []


def test_incomplete_draft_requires_an_explicit_question() -> None:
    payload = valid_result_data()
    payload["draft"]["selection_mode"] = None  # type: ignore[index]

    with pytest.raises(ValidationError):
        RequirementAnalysisResult.model_validate(payload)


def test_progressive_tiers_must_inherit_previous_slots() -> None:
    payload = valid_result_data()
    payload["draft"]["category_tiers"][1]["slots"][0]["category"] = "外套"  # type: ignore[index]

    with pytest.raises(ValidationError):
        RequirementAnalysisResult.model_validate(payload)


def test_text_values_are_trimmed_before_duplicate_checks() -> None:
    payload = valid_result_data()
    payload["draft"]["category_tiers"][0]["slots"][0]["category"] = " 上衣 "  # type: ignore[index]

    parsed = RequirementAnalysisResult.model_validate(payload)

    assert parsed.draft.category_tiers[0].slots[0].category == "上衣"
