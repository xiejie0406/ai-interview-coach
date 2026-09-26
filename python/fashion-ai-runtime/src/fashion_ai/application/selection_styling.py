"""选品与搭配工作流；负责在 Provider 输出后再次执行引用级护栏。"""

from hashlib import sha256

from fashion_ai.application.requirement_analysis import ProviderDisabledError
from fashion_ai.domain.models import SelectionStylingRequest, SelectionStylingResult
from fashion_ai.ports.selection_ranker import SelectionRanker


class DisabledSelectionRanker:
    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        del command
        raise ProviderDisabledError("AI Provider 未启用")


class SelectionStylingWorkflow:
    def __init__(self, ranker: SelectionRanker) -> None:
        self._ranker = ranker

    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        result = await self._ranker.rank(command)
        self._validate(command, result)
        return result

    @staticmethod
    def _validate(
        command: SelectionStylingRequest, result: SelectionStylingResult
    ) -> None:
        source = command.input
        if (
            result.quote_ref != source.quote_ref
            or result.quote_row_version != source.quote_row_version
            or result.candidate_set_hash != source.candidate_set_hash
        ):
            raise ValueError("选品结果与冻结方案版本不一致")
        candidates = {
            candidate.candidate_ref: candidate for candidate in source.frozen_candidates
        }
        expected_tiers = {tier.category_count: tier for tier in source.tiers}
        seen_sets: set[tuple[str, ...]] = set()
        previous_by_rank: list[dict[int, str]] = []
        if [tier.category_count for tier in result.tiers] != list(expected_tiers):
            raise ValueError("选品结果档位必须与冻结档位完全一致")
        for tier_result in result.tiers:
            spec = expected_tiers[tier_result.category_count]
            if tier_result.requested_candidate_count != spec.candidate_count:
                raise ValueError("选品结果候选数必须与冻结档位一致")
            current_by_rank: list[dict[int, str]] = []
            for combination in tier_result.combinations:
                if len(combination.items) != tier_result.category_count:
                    raise ValueError("组合槽位数必须等于品类数")
                indexes = [item.slot_index for item in combination.items]
                if indexes != list(range(1, tier_result.category_count + 1)):
                    raise ValueError("组合槽位必须从 1 连续递增")
                chosen: dict[int, str] = {}
                price = 0
                for item, slot in zip(combination.items, spec.slots, strict=True):
                    candidate = candidates.get(item.candidate_ref)
                    if candidate is None:
                        raise ValueError("选品结果包含冻结集外候选")
                    if (
                        item.category_code != slot.category
                        or candidate.category_code != slot.category
                    ):
                        raise ValueError("选品结果候选品类与槽位不一致")
                    chosen[item.slot_index] = item.candidate_ref
                    price += candidate.conservative_unit_price_minor
                signature = tuple(chosen[index] for index in sorted(chosen))
                if signature in seen_sets:
                    raise ValueError("不同档位或候选不得输出重复组合")
                seen_sets.add(signature)
                if price != combination.conservative_unit_price_minor:
                    raise ValueError("组合保守单价必须由冻结候选相加得到")
                visual_source = "|".join(
                    f"{item.slot_index}:{item.candidate_ref}:{candidates[item.candidate_ref].visual_hash}"
                    for item in combination.items
                )
                if (
                    sha256(visual_source.encode("utf-8")).hexdigest()
                    != combination.combo_visual_hash
                ):
                    raise ValueError("组合视觉摘要必须由冻结候选按槽位计算")
                if (
                    source.budget_maximum_per_set_minor is not None
                    and price > source.budget_maximum_per_set_minor
                ):
                    raise ValueError("组合超过 Java 冻结的每套预算上限")
                for lock in source.locks:
                    if (
                        lock.slot_index <= tier_result.category_count
                        and chosen.get(lock.slot_index) != lock.candidate_ref
                    ):
                        raise ValueError("选品结果未保持锁定项")
                current_by_rank.append(chosen)
            if source.selection_mode == "progressive" and previous_by_rank:
                for rank, chosen in enumerate(current_by_rank):
                    base = previous_by_rank[min(rank, len(previous_by_rank) - 1)]
                    if any(chosen.get(slot) != ref for slot, ref in base.items()):
                        raise ValueError("递进档位必须按候选顺序继承上一档组合")
            previous_by_rank = current_by_rank
