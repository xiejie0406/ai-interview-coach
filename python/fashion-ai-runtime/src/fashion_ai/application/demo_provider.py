"""本机试用的确定性演示适配器；不调用模型或外部服务。"""

import re
from hashlib import sha256

from fashion_ai.domain.models import (
    FrozenSelectionCandidate,
    ProductAttributeSuggestionRequest,
    ProductAttributeSuggestionResult,
    RequirementAnalysisRequest,
    RequirementAnalysisResult,
    SelectionStylingRequest,
    SelectionStylingResult,
)

_CATEGORIES = ("上衣", "裤子", "帽子", "鞋")
_COLORS = ("米色", "黑色", "白色", "蓝色", "绿色", "红色")


class DemoRequirementAnalyzer:
    async def analyze(
        self, command: RequirementAnalysisRequest
    ) -> RequirementAnalysisResult:
        known = command.input.known_requirements.model_dump(mode="json")
        source = command.input.source_text
        count_match = re.search(r"(\d{1,5})\s*套", source)
        categories = [category for category in _CATEGORIES if category in source]
        colors = [color for color in _COLORS if color in source]
        if not known["scheme_name"]:
            known["scheme_name"] = "本机演示服装方案"
        if not known["set_count"]:
            known["set_count"] = int(count_match.group(1)) if count_match else 10
        if not known["selection_mode"]:
            known["selection_mode"] = "independent"
        if not known["category_tiers"]:
            known["category_tiers"] = [
                {
                    "category_count": len(categories) or 1,
                    "candidate_count": 1,
                    "slots": [
                        {"slot_index": index, "category": category, "required": True}
                        for index, category in enumerate(categories or ["上衣"], 1)
                    ],
                }
            ]
        if not known["preferred_colors"] and colors:
            known["preferred_colors"] = colors
        return RequirementAnalysisResult.model_validate(
            {
                "fact_scope": "requirements_only",
                "human_confirmation_required": True,
                "draft": {"status": "pending_human_confirmation", **known},
                "unresolved_questions": [
                    "这是本机演示规则生成的草案，请人工核对套数、品类、预算和交期。"
                ],
            }
        )


class DemoProductAttributeSuggester:
    async def suggest(
        self, command: ProductAttributeSuggestionRequest
    ) -> ProductAttributeSuggestionResult:
        current = command.input.current_attributes
        return ProductAttributeSuggestionResult.model_validate(
            {
                "fact_scope": "product_attributes_only",
                "human_confirmation_required": True,
                "draft": {
                    "status": "pending_human_confirmation",
                    "product_ref": command.input.product_ref,
                    "category_code": current.category_code,
                    "color_code": current.color_code,
                    "color_name": current.color_name,
                    "season": current.season,
                    "style": "本机演示建议",
                    "observable_tags": current.tags[:20],
                },
                "ambiguities": ["本机演示结果，请按商品实物人工确认。"],
            }
        )


class DemoSelectionRanker:
    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        source = command.input
        candidates = sorted(
            source.frozen_candidates,
            key=lambda item: (item.conservative_unit_price_minor, item.candidate_ref),
        )
        locks = {item.slot_index: item.candidate_ref for item in source.locks}
        previous: dict[int, str] = {}
        tiers: list[dict[str, object]] = []
        for tier in source.tiers:
            chosen: dict[int, FrozenSelectionCandidate] = {}
            for slot in tier.slots:
                required_ref = locks.get(slot.slot_index)
                if source.selection_mode == "progressive":
                    required_ref = required_ref or previous.get(slot.slot_index)
                match = next(
                    (
                        candidate
                        for candidate in candidates
                        if candidate.category_code == slot.category
                        and candidate.candidate_ref not in {
                            item.candidate_ref for item in chosen.values()
                        }
                        and (
                            required_ref is None
                            or candidate.candidate_ref == required_ref
                        )
                    ),
                    None,
                )
                if match is None:
                    break
                chosen[slot.slot_index] = match
            combinations: list[dict[str, object]] = []
            if len(chosen) == tier.category_count:
                price = sum(
                    item.conservative_unit_price_minor for item in chosen.values()
                )
                if (
                    source.budget_maximum_per_set_minor is None
                    or price <= source.budget_maximum_per_set_minor
                ):
                    items = [
                        {
                            "slot_index": slot.slot_index,
                            "category_code": slot.category,
                            "candidate_ref": chosen[slot.slot_index].candidate_ref,
                        }
                        for slot in tier.slots
                    ]
                    visual_source = "|".join(
                        f"{item['slot_index']}:{item['candidate_ref']}:"
                        f"{chosen[item['slot_index']].visual_hash}"
                        for item in items
                    )
                    combinations.append(
                        {
                            "combo_key": f"demo-tier-{tier.category_count}-1",
                            "name": f"本机演示 {tier.category_count} 品类搭配",
                            "reason": (
                                "按冻结候选和价格排序生成；"
                                "请人工复核，非真实模型输出。"
                            ),
                            "items": items,
                            "conservative_unit_price_minor": price,
                            "combo_visual_hash": sha256(
                                visual_source.encode("utf-8")
                            ).hexdigest(),
                        }
                    )
                    previous = {
                        slot: item.candidate_ref for slot, item in chosen.items()
                    }
            tiers.append(
                {
                    "category_count": tier.category_count,
                    "requested_candidate_count": tier.candidate_count,
                    "combinations": combinations,
                    "shortage_reasons": (
                        ["本机演示只生成一组符合冻结条件的搭配；不足部分请人工补充。"]
                        if len(combinations) < tier.candidate_count
                        else []
                    ),
                }
            )
        return SelectionStylingResult.model_validate(
            {
                "fact_scope": "frozen_candidates_only",
                "human_confirmation_required": True,
                "quote_ref": source.quote_ref,
                "quote_row_version": source.quote_row_version,
                "candidate_set_hash": source.candidate_set_hash,
                "tiers": tiers,
            }
        )
