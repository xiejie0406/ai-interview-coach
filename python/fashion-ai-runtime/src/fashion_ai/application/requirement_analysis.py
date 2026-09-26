"""需求分析应用服务及安全默认实现。"""

from fashion_ai.domain.models import (
    RequirementAnalysisRequest,
    RequirementAnalysisResult,
)


class ProviderDisabledError(RuntimeError):
    """当前运行时没有获准启用模型 Provider。"""


class DisabledRequirementAnalyzer:
    """安全默认实现；绝不生成模拟或规则拼装的 AI 结果。"""

    async def analyze(
        self, command: RequirementAnalysisRequest
    ) -> RequirementAnalysisResult:
        del command
        raise ProviderDisabledError("AI Provider 未启用")
