"""HMAC v1 服务身份认证及进程内有界重放保护。"""

import asyncio
import base64
import hashlib
import heapq
import hmac
import re
import time
from collections.abc import Callable
from typing import NoReturn
from uuid import UUID, uuid4

from fashion_ai.domain.models import ErrorCode
from fashion_ai.ports.service_auth import (
    AuthenticatedService,
    NonceClaimResult,
    NonceStore,
    ServiceRequestToSign,
    SignedServiceRequest,
)
from fashion_ai.settings import HmacKey, ServiceAuthSettings

SERVICE_ID_HEADER = "x-fashion-service-id"
KEY_ID_HEADER = "x-fashion-key-id"
TIMESTAMP_HEADER = "x-fashion-timestamp"
NONCE_HEADER = "x-fashion-nonce"
AUDIENCE_HEADER = "x-fashion-audience"
CONTENT_SHA256_HEADER = "x-fashion-content-sha256"
SIGNATURE_HEADER = "x-fashion-signature"
REQUEST_ID_HEADER = "x-request-id"
CORRELATION_ID_HEADER = "x-correlation-id"
TRACEPARENT_HEADER = "traceparent"
TRACESTATE_HEADER = "tracestate"

SERVICE_ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$"
KEY_ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$"
NONCE_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$"
SHA256_PATTERN = r"^[0-9a-f]{64}$"
TIMESTAMP_PATTERN = r"^[0-9]{10,12}$"
SIGNATURE_PATTERN = r"^v1=([A-Za-z0-9_-]{43})$"
REQUEST_ID_PATTERN = (
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)
TRACEPARENT_PATTERN = r"^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$"

_SERVICE_ID = re.compile(SERVICE_ID_PATTERN)
_KEY_ID = re.compile(KEY_ID_PATTERN)
_NONCE = re.compile(NONCE_PATTERN)
_SHA256 = re.compile(SHA256_PATTERN)
_TIMESTAMP = re.compile(TIMESTAMP_PATTERN)
_SIGNATURE = re.compile(SIGNATURE_PATTERN)
_REQUEST_ID = re.compile(REQUEST_ID_PATTERN)
_TRACEPARENT = re.compile(TRACEPARENT_PATTERN)
_TRACESTATE_SIMPLE_KEY = re.compile(r"^[a-z][a-z0-9_*/-]{0,255}$")
_TRACESTATE_MULTI_KEY = re.compile(
    r"^[a-z0-9][a-z0-9_*/-]{0,240}@[a-z][a-z0-9_*/-]{0,13}$"
)


class SecurityRejection(Exception):
    """可安全映射为稳定错误信封的认证拒绝。"""

    def __init__(
        self,
        *,
        status_code: int,
        code: ErrorCode,
        message: str,
        retryable: bool = False,
        authenticate_challenge: bool = False,
    ) -> None:
        super().__init__(message)
        self.status_code: int = status_code
        self.code: ErrorCode = code
        self.message: str = message
        self.retryable: bool = retryable
        self.authenticate_challenge: bool = authenticate_challenge


class InMemoryNonceStore:
    """单进程有界 TTL nonce 存储；可通过端口替换为平台级存储。"""

    def __init__(self, *, max_entries: int) -> None:
        if max_entries <= 0:
            raise ValueError("max_entries 必须大于 0")
        self._max_entries = max_entries
        self._expires_at: dict[tuple[str, str], int] = {}
        self._expiry_heap: list[tuple[int, str, str]] = []
        self._lock = asyncio.Lock()

    async def claim(
        self,
        *,
        service_id: str,
        nonce: str,
        now_epoch_seconds: int,
        ttl_seconds: int,
    ) -> NonceClaimResult:
        key = (service_id, nonce)
        async with self._lock:
            self._discard_expired(now_epoch_seconds)
            if key in self._expires_at:
                return NonceClaimResult.REPLAY
            if len(self._expires_at) >= self._max_entries:
                return NonceClaimResult.CAPACITY_EXCEEDED
            expires_at = now_epoch_seconds + ttl_seconds
            self._expires_at[key] = expires_at
            heapq.heappush(
                self._expiry_heap,
                (expires_at, service_id, nonce),
            )
            return NonceClaimResult.CLAIMED

    def _discard_expired(self, now_epoch_seconds: int) -> None:
        while self._expiry_heap and self._expiry_heap[0][0] <= now_epoch_seconds:
            expires_at, service_id, nonce = heapq.heappop(self._expiry_heap)
            key = (service_id, nonce)
            if self._expires_at.get(key) == expires_at:
                del self._expires_at[key]


def build_canonical_request(
    *,
    method: str,
    raw_path: str,
    service_id: str,
    audience: str,
    timestamp: str,
    nonce: str,
    content_sha256: str,
) -> bytes:
    """构造 Java/Python 共享的 FASHION-HMAC-SHA256 canonical v1。"""

    return (
        "FASHION-HMAC-SHA256\n"
        f"{method.upper()}\n"
        f"{raw_path}\n"
        f"{service_id}\n"
        f"{audience}\n"
        f"{timestamp}\n"
        f"{nonce}\n"
        f"{content_sha256}"
    ).encode()


def sign_canonical_request(secret: bytes, canonical: bytes) -> str:
    """生成 `v1=<base64url(no padding)>` 签名。"""

    signature = hmac.new(secret, canonical, hashlib.sha256).digest()
    encoded = base64.urlsafe_b64encode(signature).decode("ascii").rstrip("=")
    return f"v1={encoded}"


class HmacServiceAuthenticator:
    """验证 HMAC v1、调用方/audience、时钟窗口及 nonce。"""

    def __init__(
        self,
        *,
        settings: ServiceAuthSettings,
        nonce_store: NonceStore,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self._settings = settings
        self._nonce_store = nonce_store
        self._clock = clock

    async def authenticate(self, request: SignedServiceRequest) -> AuthenticatedService:
        if not self._settings.configured:
            raise SecurityRejection(
                status_code=401,
                code="SERVICE_AUTHENTICATION_FAILED",
                message="服务认证失败",
                authenticate_challenge=True,
            )
        service_id = self._required_header(request, SERVICE_ID_HEADER)
        key_id = self._required_header(request, KEY_ID_HEADER)
        timestamp = self._required_header(request, TIMESTAMP_HEADER)
        nonce = self._required_header(request, NONCE_HEADER)
        audience = self._required_header(request, AUDIENCE_HEADER)
        content_sha256 = self._required_header(request, CONTENT_SHA256_HEADER)
        supplied_signature = self._required_header(request, SIGNATURE_HEADER)
        request_id = self._required_header(request, REQUEST_ID_HEADER)
        correlation_id = self._required_header(request, CORRELATION_ID_HEADER)
        traceparent = self._required_header(request, TRACEPARENT_HEADER)
        tracestate = self._optional_header(request, TRACESTATE_HEADER)

        normalized_request_id = _normalize_request_id(request_id)

        if (
            _SERVICE_ID.fullmatch(service_id) is None
            or _KEY_ID.fullmatch(key_id) is None
            or _SERVICE_ID.fullmatch(audience) is None
            or _NONCE.fullmatch(nonce) is None
            or _TIMESTAMP.fullmatch(timestamp) is None
            or _SHA256.fullmatch(content_sha256) is None
            or _SIGNATURE.fullmatch(supplied_signature) is None
            or normalized_request_id is None
            or _SERVICE_ID.fullmatch(correlation_id) is None
            or not _valid_traceparent(traceparent)
            or not _valid_tracestate(tracestate)
        ):
            self._raise_authentication_failed()

        actual_digest = hashlib.sha256(request.body).hexdigest()
        if not hmac.compare_digest(content_sha256, actual_digest):
            self._raise_authentication_failed()

        secret = self._settings.key_ring().get(key_id)
        if secret is None:
            self._raise_authentication_failed()

        canonical = build_canonical_request(
            method=request.method,
            raw_path=request.raw_path,
            service_id=service_id,
            audience=audience,
            timestamp=timestamp,
            nonce=nonce,
            content_sha256=content_sha256,
        )
        expected_signature = sign_canonical_request(secret, canonical)
        if not hmac.compare_digest(supplied_signature, expected_signature):
            self._raise_authentication_failed()

        now = int(self._clock())
        timestamp_value = int(timestamp)
        if abs(now - timestamp_value) > self._settings.clock_skew_seconds:
            self._raise_authentication_failed()

        if audience != self._settings.audience:
            raise SecurityRejection(
                status_code=403,
                code="SERVICE_NOT_AUTHORIZED",
                message="服务调用 audience 不受允许",
            )
        if service_id not in self._settings.allowed_service_ids:
            raise SecurityRejection(
                status_code=403,
                code="SERVICE_NOT_AUTHORIZED",
                message="服务身份无权调用 Runtime",
            )
        if request.query_string:
            raise SecurityRejection(
                status_code=403,
                code="SERVICE_NOT_AUTHORIZED",
                message="当前签名版本不允许 query 参数",
            )

        claim = await self._nonce_store.claim(
            service_id=service_id,
            nonce=nonce,
            now_epoch_seconds=now,
            ttl_seconds=self._settings.nonce_ttl_seconds,
        )
        if claim is NonceClaimResult.REPLAY:
            raise SecurityRejection(
                status_code=401,
                code="SERVICE_AUTHENTICATION_FAILED",
                message="服务认证失败",
                authenticate_challenge=True,
            )
        if claim is NonceClaimResult.CAPACITY_EXCEEDED:
            raise SecurityRejection(
                status_code=429,
                code="RATE_LIMITED",
                message="服务认证暂时过载",
                retryable=True,
            )
        return AuthenticatedService(
            service_id=service_id,
            key_id=key_id,
            audience=audience,
            request_id=normalized_request_id,
            correlation_id=correlation_id,
            traceparent=traceparent,
        )

    @staticmethod
    def _required_header(request: SignedServiceRequest, name: str) -> str:
        values = request.headers.get(name, ())
        if len(values) != 1 or not values[0] or values[0] != values[0].strip():
            raise SecurityRejection(
                status_code=401,
                code="SERVICE_AUTHENTICATION_FAILED",
                message="服务认证失败",
                authenticate_challenge=True,
            )
        return values[0]

    @staticmethod
    def _optional_header(
        request: SignedServiceRequest,
        name: str,
    ) -> str | None:
        values = request.headers.get(name, ())
        if not values:
            return None
        if len(values) != 1:
            HmacServiceAuthenticator._raise_authentication_failed()
        return values[0]

    @staticmethod
    def _raise_authentication_failed() -> NoReturn:
        raise SecurityRejection(
            status_code=401,
            code="SERVICE_AUTHENTICATION_FAILED",
            message="服务认证失败",
            authenticate_challenge=True,
        )


class ServiceSigningUnavailableError(RuntimeError):
    """出站 active key 未配置，禁止发送未认证请求。"""


def _new_nonce() -> str:
    return str(uuid4())


class HmacServiceRequestSigner:
    """Python→Java 的 HMAC v1 active-key signer，不执行网络调用。"""

    def __init__(
        self,
        *,
        active_key: HmacKey | None,
        service_id: str = "fashion-ai-runtime",
        audience: str = "ruoyi-fashion",
        clock: Callable[[], float] = time.time,
        nonce_factory: Callable[[], str] = _new_nonce,
    ) -> None:
        if _SERVICE_ID.fullmatch(service_id) is None:
            raise ValueError("出站 service id 格式无效")
        if _SERVICE_ID.fullmatch(audience) is None:
            raise ValueError("出站 audience 格式无效")
        self._active_key = active_key
        self._service_id = service_id
        self._audience = audience
        self._clock = clock
        self._nonce_factory = nonce_factory

    @property
    def configured(self) -> bool:
        return self._active_key is not None

    def sign(self, request: ServiceRequestToSign) -> dict[str, str]:
        key = self._active_key
        if key is None:
            raise ServiceSigningUnavailableError("出站服务认证尚未配置")
        if request.query_string or "?" in request.raw_path:
            raise ValueError("HMAC v1 不允许 query 参数")
        if not request.method or not request.raw_path.startswith("/"):
            raise ValueError("待签名请求方法或 path 无效")
        try:
            request.raw_path.encode("ascii")
        except UnicodeEncodeError as exc:
            raise ValueError("raw path 必须是 ASCII 或百分号编码") from exc

        request_id = _normalize_request_id(request.request_id)
        if request_id is None:
            raise ValueError("X-Request-Id 格式无效")
        if _SERVICE_ID.fullmatch(request.correlation_id) is None:
            raise ValueError("X-Correlation-Id 格式无效")
        if not _valid_traceparent(request.traceparent):
            raise ValueError("traceparent 格式无效")
        if not _valid_tracestate(request.tracestate):
            raise ValueError("tracestate 格式无效")

        timestamp = str(int(self._clock()))
        nonce = self._nonce_factory()
        if _TIMESTAMP.fullmatch(timestamp) is None:
            raise ValueError("出站 timestamp 格式无效")
        if _NONCE.fullmatch(nonce) is None:
            raise ValueError("出站 nonce 格式无效")

        digest = hashlib.sha256(request.body).hexdigest()
        canonical = build_canonical_request(
            method=request.method,
            raw_path=request.raw_path,
            service_id=self._service_id,
            audience=self._audience,
            timestamp=timestamp,
            nonce=nonce,
            content_sha256=digest,
        )
        headers = {
            "X-Fashion-Service-Id": self._service_id,
            "X-Fashion-Key-Id": key.key_id,
            "X-Fashion-Timestamp": timestamp,
            "X-Fashion-Nonce": nonce,
            "X-Fashion-Audience": self._audience,
            "X-Fashion-Content-SHA256": digest,
            "X-Fashion-Signature": sign_canonical_request(
                key.secret,
                canonical,
            ),
            "X-Request-Id": request_id,
            "X-Correlation-Id": request.correlation_id,
            "traceparent": request.traceparent,
        }
        if request.tracestate is not None:
            headers["tracestate"] = request.tracestate
        return headers


def _normalize_request_id(value: str) -> str | None:
    if _REQUEST_ID.fullmatch(value) is None:
        return None
    try:
        return str(UUID(value))
    except ValueError:
        return None


def _valid_traceparent(value: str) -> bool:
    parts = value.split("-")
    return bool(
        _TRACEPARENT.fullmatch(value) is not None
        and parts[1] != "0" * 32
        and parts[2] != "0" * 16
    )


def _valid_tracestate(value: str | None) -> bool:
    if value is None:
        return True
    if not value or len(value) > 512:
        return False
    members = value.split(",")
    if len(members) > 32:
        return False
    seen_keys: set[str] = set()
    for raw_member in members:
        member = raw_member.strip(" \t")
        if member.count("=") != 1:
            return False
        key, item_value = member.split("=", maxsplit=1)
        if (
            key in seen_keys
            or (
                _TRACESTATE_SIMPLE_KEY.fullmatch(key) is None
                and _TRACESTATE_MULTI_KEY.fullmatch(key) is None
            )
            or not item_value
            or len(item_value) > 256
            or item_value[-1] == " "
            or any(
                ord(char) < 32 or ord(char) > 126 or char in {",", "="}
                for char in item_value
            )
        ):
            return False
        seen_keys.add(key)
    return True
