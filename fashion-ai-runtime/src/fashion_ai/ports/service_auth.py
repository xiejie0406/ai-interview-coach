"""入站服务身份认证和重放保护端口。"""

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol


@dataclass(frozen=True, slots=True)
class SignedServiceRequest:
    """验证签名所需的最小原始请求事实。"""

    method: str
    raw_path: str
    query_string: bytes
    body: bytes
    headers: Mapping[str, tuple[str, ...]]


@dataclass(frozen=True, slots=True)
class ServiceRequestToSign:
    """Python 调用 Java 时必须签名的原始请求事实与关联标识。"""

    method: str
    raw_path: str
    query_string: bytes
    body: bytes
    request_id: str
    correlation_id: str
    traceparent: str
    tracestate: str | None = None


@dataclass(frozen=True, slots=True)
class AuthenticatedService:
    """已经通过密码学验证和授权检查的服务身份。"""

    service_id: str
    key_id: str
    audience: str
    request_id: str
    correlation_id: str
    traceparent: str


class NonceClaimResult(StrEnum):
    """一次 nonce 原子声明的结果。"""

    CLAIMED = "claimed"
    REPLAY = "replay"
    CAPACITY_EXCEEDED = "capacity_exceeded"


class NonceStore(Protocol):
    """重放 nonce 的有界、原子 TTL 存储端口。"""

    async def claim(
        self,
        *,
        service_id: str,
        nonce: str,
        now_epoch_seconds: int,
        ttl_seconds: int,
    ) -> NonceClaimResult: ...


class ServiceAuthenticator(Protocol):
    """入站服务认证适配器必须实现的端口。"""

    async def authenticate(
        self, request: SignedServiceRequest
    ) -> AuthenticatedService: ...


class ServiceRequestSigner(Protocol):
    """Python→Java 请求的 active-key 签名端口。"""

    @property
    def configured(self) -> bool: ...

    def sign(self, request: ServiceRequestToSign) -> Mapping[str, str]: ...
