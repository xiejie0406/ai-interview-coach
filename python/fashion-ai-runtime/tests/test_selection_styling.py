import asyncio
from datetime import UTC, datetime, timedelta
from hashlib import sha256
from typing import cast
from uuid import uuid4

from fastapi.testclient import TestClient
from support import runtime_settings, signed_json_post

from fashion_ai.application import SelectionStylingWorkflow
from fashion_ai.domain.models import (
    SelectionComboItem,
    SelectionStylingRequest,
    SelectionStylingResult,
)
from fashion_ai.main import create_app


def _hash(value: str) -> str:
    return sha256(value.encode("utf-8")).hexdigest()


def valid_request() -> dict[str, object]:
    candidates: list[dict[str, object]] = []
    for index, category in enumerate(("上衣", "裤子", "帽子", "鞋"), start=1):
        candidates.append(
            {
                "candidate_ref": f"SRC:STYLE-{index}:COLOR-{index}",
                "category_code": category,
                "source_ref": "SRC",
                "style_ref": f"STYLE-{index}",
                "color_code": f"COLOR-{index}",
                "color_name": "米色",
                "product_name": f"商品{index}",
                "season": "秋季",
                "tags": ["休闲"],
                "conservative_unit_price_minor": 5_000,
                "total_available_qty": 120,
                "visual_hash": _hash(f"candidate-{index}"),
                "variants": [
                    {
                        "product_ref": str(index),
                        "product_row_version": 1,
                        "sku_ref": f"SKU-{index}",
                        "size_code": "M",
                        "unit_price_minor": 5_000,
                        "available_qty": 120,
                    }
                ],
            }
        )
    return {
        "version": "1.0",
        "request_id": str(uuid4()),
        "correlation_id": "RUN-10001",
        "run_id": "RUN-10001",
        "idempotency_key": f"selection:{uuid4()}",
        "deadline_at": (datetime.now(UTC) + timedelta(minutes=2)).isoformat(),
        "input": {
            "quote_ref": "100",
            "quote_row_version": 3,
            "selection_mode": "progressive",
            "requested_qty": 100,
            "budget_maximum_per_set_minor": 30_000,
            "requirements": {
                "preferred_colors": ["米色"],
                "exclusions": [],
                "size_requirements": [],
                "category_tiers": [],
                "budget_constraints": [],
            },
            "tiers": [
                {
                    "category_count": count,
                    "candidate_count": 1,
                    "slots": [
                        {"slot_index": slot, "category": category, "required": True}
                        for slot, category in enumerate(
                            ("上衣", "裤子", "帽子", "鞋")[:count], start=1
                        )
                    ],
                }
                for count in range(1, 5)
            ],
            "frozen_candidates": candidates,
            "locks": [
                {
                    "slot_index": 1,
                    "category_code": "上衣",
                    "candidate_ref": "SRC:STYLE-1:COLOR-1",
                    "candidate_visual_hash": candidates[0]["visual_hash"],
                }
            ],
            "candidate_set_hash": _hash("candidate-set"),
        },
    }


def valid_result(command: SelectionStylingRequest) -> SelectionStylingResult:
    by_category = {
        candidate.category_code: candidate
        for candidate in command.input.frozen_candidates
    }
    tiers: list[dict[str, object]] = []
    for tier in command.input.tiers:
        items = [
            {
                "slot_index": slot.slot_index,
                "category_code": slot.category,
                "candidate_ref": by_category[slot.category].candidate_ref,
            }
            for slot in tier.slots
        ]
        typed_items = [SelectionComboItem.model_validate(item) for item in items]
        visual_source = "|".join(
            f"{item.slot_index}:{item.candidate_ref}:{by_category[item.category_code].visual_hash}"
            for item in typed_items
        )
        tiers.append(
            {
                "category_count": tier.category_count,
                "requested_candidate_count": tier.candidate_count,
                "combinations": [
                    {
                        "combo_key": f"tier-{tier.category_count}-candidate-1",
                        "name": f"{tier.category_count} 品类搭配",
                        "reason": "符合冻结偏好与硬约束",
                        "items": items,
                        "conservative_unit_price_minor": 5_000 * tier.category_count,
                        "combo_visual_hash": _hash(visual_source),
                    }
                ],
                "shortage_reasons": [],
            }
        )
    return SelectionStylingResult.model_validate(
        {
            "fact_scope": "frozen_candidates_only",
            "human_confirmation_required": True,
            "quote_ref": command.input.quote_ref,
            "quote_row_version": command.input.quote_row_version,
            "candidate_set_hash": command.input.candidate_set_hash,
            "tiers": tiers,
        }
    )


class SuccessfulRanker:
    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        return valid_result(command)


class InventingRanker:
    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        result = valid_result(command).model_dump(mode="json")
        result["tiers"][0]["combinations"][0]["items"][0]["candidate_ref"] = (
            "SRC:INVENTED:SKU"
        )
        return SelectionStylingResult.model_validate(result)


class NoSolutionRanker:
    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        return SelectionStylingResult.model_validate(
            {
                "fact_scope": "frozen_candidates_only",
                "human_confirmation_required": True,
                "quote_ref": command.input.quote_ref,
                "quote_row_version": command.input.quote_row_version,
                "candidate_set_hash": command.input.candidate_set_hash,
                "tiers": [
                    {
                        "category_count": tier.category_count,
                        "requested_candidate_count": tier.candidate_count,
                        "combinations": [],
                        "shortage_reasons": ["冻结候选无法满足当前档位"],
                    }
                    for tier in command.input.tiers
                ],
            }
        )


def test_selection_styling_accepts_four_progressive_tiers_and_lock() -> None:
    payload = valid_request()
    with TestClient(
        create_app(settings=runtime_settings(), selection_ranker=SuccessfulRanker())
    ) as client:
        response = signed_json_post(client, "/internal/v1/selection-styling", payload)
    assert response.status_code == 200
    result = response.json()["result"]
    assert [tier["category_count"] for tier in result["tiers"]] == [1, 2, 3, 4]
    assert all(
        tier["combinations"][0]["items"][0]["candidate_ref"] == "SRC:STYLE-1:COLOR-1"
        for tier in result["tiers"]
    )


def test_selection_styling_rejects_invented_candidate() -> None:
    payload = valid_request()
    with TestClient(
        create_app(settings=runtime_settings(), selection_ranker=InventingRanker())
    ) as client:
        response = signed_json_post(client, "/internal/v1/selection-styling", payload)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "REQUEST_VALIDATION_FAILED"


def test_selection_styling_disabled_is_explicit() -> None:
    payload = valid_request()
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = signed_json_post(client, "/internal/v1/selection-styling", payload)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "PROVIDER_DISABLED"


def test_selection_input_accepts_a_published_zero_price() -> None:
    payload = valid_request()
    source = cast(dict[str, object], payload["input"])
    candidates = cast(list[dict[str, object]], source["frozen_candidates"])
    for candidate in candidates:
        candidate["conservative_unit_price_minor"] = 0
        variants = cast(list[dict[str, object]], candidate["variants"])
        variants[0]["unit_price_minor"] = 0

    command = SelectionStylingRequest.model_validate(payload)

    assert all(
        candidate.conservative_unit_price_minor == 0
        for candidate in command.input.frozen_candidates
    )


def test_selection_styling_returns_explicit_shortages_when_no_solution() -> None:
    payload = valid_request()
    with TestClient(
        create_app(settings=runtime_settings(), selection_ranker=NoSolutionRanker())
    ) as client:
        response = signed_json_post(client, "/internal/v1/selection-styling", payload)
    assert response.status_code == 200
    assert all(not tier["combinations"] for tier in response.json()["result"]["tiers"])
    assert all(
        tier["shortage_reasons"] for tier in response.json()["result"]["tiers"]
    )


def test_independent_tiers_may_choose_a_different_earlier_slot() -> None:
    payload = valid_request()
    source = cast(dict[str, object], payload["input"])
    source["selection_mode"] = "independent"
    source["locks"] = []
    candidates = cast(list[dict[str, object]], source["frozen_candidates"])
    alternative = candidates[0].copy()
    alternative.update(
        {
            "candidate_ref": "SRC:STYLE-5:COLOR-5",
            "style_ref": "STYLE-5",
            "color_code": "COLOR-5",
            "visual_hash": _hash("candidate-5"),
            "variants": [
                {
                    "product_ref": "5",
                    "product_row_version": 1,
                    "sku_ref": "SKU-5",
                    "size_code": "M",
                    "unit_price_minor": 5_000,
                    "available_qty": 120,
                }
            ],
        }
    )
    candidates.append(alternative)
    command = SelectionStylingRequest.model_validate(payload)

    class IndependentRanker:
        async def rank(
            self, command: SelectionStylingRequest
        ) -> SelectionStylingResult:
            result = valid_result(command).model_dump(mode="json")
            first = result["tiers"][0]["combinations"][0]
            first["items"][0]["candidate_ref"] = "SRC:STYLE-1:COLOR-1"
            first["combo_visual_hash"] = _hash(
                f"1:SRC:STYLE-1:COLOR-1:{_hash('candidate-1')}"
            )
            return SelectionStylingResult.model_validate(result)

    result = asyncio.run(SelectionStylingWorkflow(IndependentRanker()).rank(command))
    assert (
        result.tiers[0].combinations[0].items[0].candidate_ref
        != result.tiers[1].combinations[0].items[0].candidate_ref
    )


def test_workflow_rejects_changed_candidate_set_hash() -> None:
    command = SelectionStylingRequest.model_validate(valid_request())

    class ChangedHashRanker:
        async def rank(
            self, command: SelectionStylingRequest
        ) -> SelectionStylingResult:
            result = valid_result(command).model_dump(mode="json")
            result["candidate_set_hash"] = _hash("changed")
            return SelectionStylingResult.model_validate(result)

    workflow = SelectionStylingWorkflow(ChangedHashRanker())
    try:
        asyncio.run(workflow.rank(command))
    except ValueError as exception:
        assert "冻结方案版本" in str(exception)
    else:
        raise AssertionError("changed candidate set hash should be rejected")
