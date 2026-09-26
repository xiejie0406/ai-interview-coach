"""默认 fail-closed 的 Agent Runtime 配置。"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class RuntimeSettings(BaseModel):
    """当前 Feature 不提供任何可启用真实 Provider 的配置分支。"""

    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)

    mode: Literal["offline_fake"] = "offline_fake"
    provider_disabled: Literal[True] = True
    provider_mode: Literal["provider_disabled"] = "provider_disabled"
    transport: Literal["fake"] = "fake"
    outbound_network: Literal["disabled"] = "disabled"
    uia_enabled: Literal[False] = False
    shell_enabled: Literal[False] = False
    browser_enabled: Literal[False] = False
    arbitrary_file_access_enabled: Literal[False] = False
    external_write_enabled: Literal[False] = False
    external_actions_enabled: Literal[False] = False
    listen_http: Literal[False] = False
    allowed_tool_names: frozenset[str] = Field(
        default_factory=lambda: frozenset({"context.lookup", "proposal.compose"})
    )
    max_model_calls: int = Field(default=1, ge=1, le=1)
