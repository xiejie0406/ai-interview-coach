"""运行时配置；服务认证 Secret 仅接收若依 MySQL 密钥启动器的管道交付。"""

import base64
import binascii
import json
import os
import re
import sys
from collections.abc import Mapping
from dataclasses import dataclass, field
from enum import StrEnum

API_VERSION = "1.0"
SERVICE_NAME = "fashion-ai-runtime"
SERVICE_VERSION = "0.1.0"
DEFAULT_MAX_JSON_BODY_BYTES = 64 * 1024
DEFAULT_CLOCK_SKEW_SECONDS = 300
DEFAULT_NONCE_TTL_SECONDS = 600
DEFAULT_MAX_NONCE_ENTRIES = 100_000

_STANDARD_BASE64 = re.compile(r"^[A-Za-z0-9+/]+={0,2}$")
_URLSAFE_BASE64 = re.compile(r"^[A-Za-z0-9_-]+={0,2}$")
_SERVICE_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")
_KEY_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")


class ProviderMode(StrEnum):
    """当前允许的 Provider 模式。"""

    DISABLED = "disabled"
    DEMO = "demo"


@dataclass(frozen=True, slots=True)
class HmacKey:
    """可按 key id 轮换的 HMAC 密钥；repr 不得泄漏 Secret。"""

    key_id: str
    secret: bytes = field(repr=False)

    def __post_init__(self) -> None:
        if _KEY_ID.fullmatch(self.key_id) is None:
            raise ValueError("HMAC key id 必须是 1 到 64 位稳定标识")
        if len(self.secret) < 32:
            raise ValueError("HMAC secret 至少需要 32 字节")


@dataclass(frozen=True, slots=True)
class ServiceAuthSettings:
    """Java 调用 Python 的入站服务认证策略。"""

    audience: str = SERVICE_NAME
    allowed_service_ids: frozenset[str] = frozenset({"ruoyi-fashion"})
    active_key: HmacKey | None = None
    previous_key: HmacKey | None = None
    clock_skew_seconds: int = DEFAULT_CLOCK_SKEW_SECONDS
    nonce_ttl_seconds: int = DEFAULT_NONCE_TTL_SECONDS

    def __post_init__(self) -> None:
        if _SERVICE_ID.fullmatch(self.audience) is None:
            raise ValueError("认证 audience 必须是 1 到 100 位稳定服务标识")
        if not self.allowed_service_ids:
            raise ValueError("至少配置一个允许调用 Runtime 的服务身份")
        if any(
            _SERVICE_ID.fullmatch(service_id) is None
            for service_id in self.allowed_service_ids
        ):
            raise ValueError("调用方 service id 必须是 1 到 100 位稳定服务标识")
        if self.clock_skew_seconds <= 0:
            raise ValueError("认证时钟偏差必须大于 0 秒")
        if self.nonce_ttl_seconds < DEFAULT_NONCE_TTL_SECONDS:
            raise ValueError("nonce TTL 至少为 600 秒")
        if self.nonce_ttl_seconds < self.clock_skew_seconds * 2:
            raise ValueError("nonce TTL 不得小于时钟偏差的两倍")
        if self.previous_key is not None and self.active_key is None:
            raise ValueError("配置 previous key 时必须同时配置 active key")
        if (
            self.active_key is not None
            and self.previous_key is not None
            and self.active_key.key_id == self.previous_key.key_id
        ):
            raise ValueError("active key 与 previous key 必须使用不同 key id")

    @property
    def configured(self) -> bool:
        return self.active_key is not None

    def key_ring(self) -> dict[str, bytes]:
        keys: dict[str, bytes] = {}
        if self.active_key is not None:
            keys[self.active_key.key_id] = self.active_key.secret
        if self.previous_key is not None:
            keys[self.previous_key.key_id] = self.previous_key.secret
        return keys


@dataclass(frozen=True, slots=True)
class RuntimeSettings:
    """安全默认配置。

    默认 disabled；demo 仅用于显式启用的本机演示。
    认证密钥没有默认值；未配置时内部接口 fail closed。
    """

    provider_mode: ProviderMode = ProviderMode.DISABLED
    max_json_body_bytes: int = DEFAULT_MAX_JSON_BODY_BYTES
    max_nonce_entries: int = DEFAULT_MAX_NONCE_ENTRIES
    service_auth: ServiceAuthSettings = field(default_factory=ServiceAuthSettings)
    log_level: str = "INFO"

    def __post_init__(self) -> None:
        if self.max_json_body_bytes < 1024:
            raise ValueError("JSON 请求体上限不得小于 1024 字节")
        if self.max_json_body_bytes > 10 * 1024 * 1024:
            raise ValueError("JSON 请求体上限不得大于 10 MiB")
        if self.max_nonce_entries < 1_000:
            raise ValueError("nonce 容量不得小于 1000")
        if self.log_level.upper() not in {
            "CRITICAL",
            "ERROR",
            "WARNING",
            "INFO",
            "DEBUG",
        }:
            raise ValueError("不支持的日志级别")

    @classmethod
    def from_env(
        cls,
        environ: Mapping[str, str] | None = None,
        managed_payload: Mapping[str, object] | None = None,
    ) -> "RuntimeSettings":
        """非秘密配置来自环境；HMAC 值来自若依启动器的单次管道消息。"""

        source = os.environ if environ is None else environ
        provider_value = source.get("FASHION_AI_PROVIDER_MODE", "disabled")
        try:
            provider_mode = ProviderMode(provider_value)
        except ValueError as exc:
            raise ValueError("当前只允许 disabled 或 demo Provider") from exc

        if any(name.startswith(("FASHION_AI_AUTH_ACTIVE_", "FASHION_AI_AUTH_PREVIOUS_")) for name in source):
            raise ValueError("旧 HMAC 环境变量已停用，请通过若依 MySQL 密钥启动器运行")
        if managed_payload is None and source.get("FASHION_AI_MANAGED_SECRET_STDIN") == "1":
            raw = sys.stdin.buffer.readline(16_385)
            if not raw or len(raw) > 16_384:
                raise ValueError("若依密钥管道消息缺失或过长")
            try:
                managed_payload = json.loads(raw)
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise ValueError("若依密钥管道消息无效") from exc
        managed_payload = managed_payload or {}
        active_key = _read_managed_key_pair(managed_payload, "active")
        previous_key = _read_managed_key_pair(managed_payload, "previous")
        allowed_services = frozenset(
            item.strip()
            for item in source.get(
                "FASHION_AI_AUTH_ALLOWED_SERVICES", "ruoyi-fashion"
            ).split(",")
            if item.strip()
        )
        return cls(
            provider_mode=provider_mode,
            max_json_body_bytes=_read_int(
                source,
                "FASHION_AI_MAX_JSON_BODY_BYTES",
                DEFAULT_MAX_JSON_BODY_BYTES,
            ),
            max_nonce_entries=_read_int(
                source,
                "FASHION_AI_AUTH_MAX_NONCE_ENTRIES",
                DEFAULT_MAX_NONCE_ENTRIES,
            ),
            service_auth=ServiceAuthSettings(
                allowed_service_ids=allowed_services,
                active_key=active_key,
                previous_key=previous_key,
                clock_skew_seconds=_read_int(
                    source,
                    "FASHION_AI_AUTH_CLOCK_SKEW_SECONDS",
                    DEFAULT_CLOCK_SKEW_SECONDS,
                ),
                nonce_ttl_seconds=_read_int(
                    source,
                    "FASHION_AI_AUTH_NONCE_TTL_SECONDS",
                    DEFAULT_NONCE_TTL_SECONDS,
                ),
            ),
            log_level=source.get("FASHION_AI_LOG_LEVEL", "INFO").upper(),
        )


def _read_managed_key_pair(payload: Mapping[str, object], slot: str) -> HmacKey | None:
    pair = payload.get(slot)
    if pair is None:
        return None
    if not isinstance(pair, dict):
        raise ValueError(f"{slot} 密钥组格式无效")
    key_id = pair.get("keyId")
    encoded_secret = pair.get("keyBase64")
    if not key_id or not encoded_secret:
        raise ValueError(f"{slot} key id 与 Base64 key 必须同时配置")
    if not isinstance(key_id, str) or not isinstance(encoded_secret, str):
        raise ValueError(f"{slot} 密钥组格式无效")
    return HmacKey(
        key_id=key_id,
        secret=_decode_base64_key(encoded_secret, slot.upper()),
    )


def _decode_base64_key(encoded: str, slot: str) -> bytes:
    if encoded != encoded.strip():
        raise ValueError(f"{slot.lower()} key 必须是合法 Base64")
    padded = encoded + ("=" * (-len(encoded) % 4))
    try:
        if _STANDARD_BASE64.fullmatch(encoded) is not None:
            decoded = base64.b64decode(padded, validate=True)
        elif _URLSAFE_BASE64.fullmatch(encoded) is not None:
            decoded = base64.b64decode(
                padded,
                altchars=b"-_",
                validate=True,
            )
        else:
            raise ValueError
    except (binascii.Error, ValueError) as exc:
        raise ValueError(f"{slot.lower()} key 必须是合法 Base64") from exc
    if not decoded:
        raise ValueError(f"{slot.lower()} key 解码后不得为空")
    return decoded


def _read_int(source: Mapping[str, str], name: str, default: int) -> int:
    value = source.get(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise ValueError(f"{name} 必须是整数") from exc
