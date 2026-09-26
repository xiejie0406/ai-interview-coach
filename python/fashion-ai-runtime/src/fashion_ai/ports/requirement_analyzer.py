"""需求分析 Provider 端口。"""

from typing import Protocol

from fashion_ai.domain.models import (
    RequirementAnalysisRequest,
    RequirementAnalysisResult,
)


class RequirementAnalyzer(Protocol):
    """后续 PydanticAI adapter 必须实现的窄端口。"""

    async def analyze(
        self, command: RequirementAnalysisRequest
    ) -> RequirementAnalysisResult: ...
