"""Runner 的 fail-closed 配置与 loopback Origin 边界。"""

from ipaddress import ip_address
from typing import Literal
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field, field_validator


class RunnerBaselineSettings(BaseModel):
    """在 IMP-05 前将所有真实执行能力固定为关闭。"""

    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)

    mode: Literal["baseline"] = "baseline"
    outbound_network: Literal["disabled"] = "disabled"
    uia_enabled: Literal[False] = False
    shell_enabled: Literal[False] = False
    browser_enabled: Literal[False] = False
    arbitrary_file_access_enabled: Literal[False] = False
    external_write_enabled: Literal[False] = False


class RunnerSettings(BaseModel):
    """IMP-05 合成模拟器配置；唯一网络出口是显式 loopback RuoYi Origin。"""

    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)

    origin: str
    credential_token: str = Field(min_length=3, repr=False)
    capacity: int = Field(default=1, ge=1, le=32)
    batch_limit: int = Field(default=1, ge=1, le=16)
    protocol_version: Literal[1] = 1
    runner_version: str = Field(default="0.1.0", min_length=1, max_length=64)
    uia_enabled: Literal[False] = False
    shell_enabled: Literal[False] = False
    browser_enabled: Literal[False] = False
    arbitrary_file_access_enabled: Literal[False] = False
    external_write_enabled: Literal[False] = False

    @field_validator("origin")
    @classmethod
    def loopback_origin_only(cls, value: str) -> str:
        parsed = urlsplit(value)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("origin 必须是 HTTP(S) loopback Origin")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("origin 不得包含凭据、查询或 fragment")
        if parsed.path not in {"", "/"}:
            raise ValueError("origin 只能配置 Origin，不得包含业务路径")
        host = parsed.hostname.lower()
        loopback = host == "localhost"
        if not loopback:
            try:
                loopback = ip_address(host).is_loopback
            except ValueError:
                loopback = False
        if not loopback:
            raise ValueError("Runner 只允许连接 loopback RuoYi Origin")
        return value.rstrip("/")
