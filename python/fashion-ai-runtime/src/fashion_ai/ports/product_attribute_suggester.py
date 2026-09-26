"""商品属性建议 Provider 窄端口。"""

from typing import Protocol

from fashion_ai.domain.models import (
    ProductAttributeSuggestionRequest,
    ProductAttributeSuggestionResult,
)


class ProductAttributeSuggester(Protocol):
    async def suggest(
        self, command: ProductAttributeSuggestionRequest
    ) -> ProductAttributeSuggestionResult: ...
