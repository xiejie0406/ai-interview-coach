"""冻结候选选品排序端口。"""

from typing import Protocol

from fashion_ai.domain.models import SelectionStylingRequest, SelectionStylingResult


class SelectionRanker(Protocol):
    async def rank(self, command: SelectionStylingRequest) -> SelectionStylingResult:
        """只在 Java 提供的冻结候选引用中排序和组套。"""
        ...
