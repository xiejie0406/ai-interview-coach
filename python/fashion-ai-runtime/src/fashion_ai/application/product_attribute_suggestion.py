"""商品属性建议应用服务的安全默认实现。"""

from fashion_ai.application.requirement_analysis import ProviderDisabledError
from fashion_ai.domain.models import (
    ProductAttributeSuggestionRequest,
    ProductAttributeSuggestionResult,
)


class DisabledProductAttributeSuggester:
    """未配置 Provider 时明确失败，不生成规则或样例结果。"""

    async def suggest(
        self, command: ProductAttributeSuggestionRequest
    ) -> ProductAttributeSuggestionResult:
        del command
        raise ProviderDisabledError("AI Provider 未启用")
