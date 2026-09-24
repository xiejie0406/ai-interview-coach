"""解析前正文限制与内部服务认证 ASGI 中间件。"""

from collections.abc import Mapping
from typing import NoReturn

from fastapi.responses import JSONResponse
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from fashion_ai.api.security import SecurityRejection
from fashion_ai.domain.models import ErrorDetail, ErrorEnvelope
from fashion_ai.ports.service_auth import ServiceAuthenticator, SignedServiceRequest
from fashion_ai.telemetry import current_correlation_id, current_trace_id


class RequestSecurityMiddleware:
    """先限制实际字节，再对 `/internal/**` 验证 HMAC 服务身份。"""

    def __init__(
        self,
        app: ASGIApp,
        *,
        max_body_bytes: int,
        authenticator: ServiceAuthenticator,
    ) -> None:
        self._app = app
        self._max_body_bytes = max_body_bytes
        self._authenticator = authenticator

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        headers = _headers_from_scope(scope)
        try:
            body = await self._read_body(headers=headers, receive=receive)
            state = scope.setdefault("state", {})
            state["fashion_request_size"] = len(body)
            if str(scope.get("path", "")).startswith("/internal/"):
                identity = await self._authenticator.authenticate(
                    SignedServiceRequest(
                        method=str(scope.get("method", "GET")),
                        raw_path=_raw_path(scope),
                        query_string=bytes(scope.get("query_string", b"")),
                        body=body,
                        headers=headers,
                    )
                )
                state["fashion_authenticated_service"] = identity
        except SecurityRejection as rejection:
            state = scope.setdefault("state", {})
            state["fashion_error_code"] = rejection.code
            await _send_rejection(scope, receive, send, rejection)
            return

        received = False

        async def replay_body() -> Message:
            nonlocal received
            if not received:
                received = True
                return {"type": "http.request", "body": body, "more_body": False}
            return {"type": "http.disconnect"}

        await self._app(scope, replay_body, send)

    async def _read_body(
        self,
        *,
        headers: Mapping[str, tuple[str, ...]],
        receive: Receive,
    ) -> bytes:
        declared_length = _content_length(headers)
        if declared_length is not None and declared_length > self._max_body_bytes:
            _raise_body_too_large()

        body = bytearray()
        more_body = True
        while more_body:
            message = await receive()
            if message["type"] == "http.disconnect":
                break
            if message["type"] != "http.request":
                continue
            chunk = message.get("body", b"")
            body.extend(chunk)
            if len(body) > self._max_body_bytes:
                _raise_body_too_large()
            more_body = bool(message.get("more_body", False))
        return bytes(body)


def _content_length(headers: Mapping[str, tuple[str, ...]]) -> int | None:
    values = headers.get("content-length", ())
    if not values:
        return None
    if len(values) != 1:
        _raise_body_too_large()
    value = values[0]
    if len(value) > 20 or not value.isascii() or not value.isdigit():
        _raise_body_too_large()
    return int(value)


def _raise_body_too_large() -> NoReturn:
    raise SecurityRejection(
        status_code=413,
        code="PAYLOAD_TOO_LARGE",
        message="请求体超过 Runtime 允许的字节上限",
    )


def _headers_from_scope(scope: Scope) -> dict[str, tuple[str, ...]]:
    grouped: dict[str, list[str]] = {}
    for raw_name, raw_value in scope.get("headers", []):
        name = raw_name.decode("latin-1").lower()
        grouped.setdefault(name, []).append(raw_value.decode("latin-1"))
    return {name: tuple(values) for name, values in grouped.items()}


def _raw_path(scope: Scope) -> str:
    raw_path = scope.get("raw_path")
    if isinstance(raw_path, bytes):
        return raw_path.decode("ascii", errors="strict")
    return str(scope.get("path", "/"))


async def _send_rejection(
    scope: Scope,
    receive: Receive,
    send: Send,
    rejection: SecurityRejection,
) -> None:
    envelope = ErrorEnvelope(
        version="1.0",
        request_id=None,
        correlation_id=current_correlation_id() or None,
        run_id=None,
        trace_id=current_trace_id(),
        error=ErrorDetail(
            code=rejection.code,
            message=rejection.message,
            retryable=rejection.retryable,
        ),
    )
    headers: dict[str, str] = {}
    if rejection.authenticate_challenge:
        headers["WWW-Authenticate"] = 'FashionHmac realm="fashion-ai-runtime"'
    response = JSONResponse(
        status_code=rejection.status_code,
        content=envelope.model_dump(mode="json"),
        headers=headers,
    )
    await response(scope, receive, send)
