"""不绑定采集端的 OTel trace/metrics 和白名单结构日志。"""

import contextvars
import json
import logging
import re
import time
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import cast
from uuid import uuid4

from opentelemetry import trace
from opentelemetry.metrics import Counter, Histogram, Meter
from opentelemetry.propagators.textmap import Getter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.trace import SpanKind, Tracer
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from fashion_ai.ports.service_auth import AuthenticatedService

CORRELATION_ID_HEADER = "x-correlation-id"
_CORRELATION_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")
_correlation_id: contextvars.ContextVar[str] = contextvars.ContextVar(
    "fashion_ai_correlation_id", default=""
)
_business_ids: contextvars.ContextVar[tuple[str | None, str | None]] = (
    contextvars.ContextVar("fashion_ai_business_ids", default=(None, None))
)

_LOG_FIELDS = (
    "event",
    "correlation_id",
    "trace_id",
    "method",
    "path",
    "status_code",
    "duration_ms",
    "request_size",
    "service_id",
    "request_id",
    "run_id",
    "error_code",
)


class StructuredJsonFormatter(logging.Formatter):
    """只序列化批准字段，避免意外输出正文、认证头或 Secret。"""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
        }
        for field_name in _LOG_FIELDS:
            value = getattr(record, field_name, None)
            if value is not None and value != "":
                payload[field_name] = value
        if "event" not in payload:
            payload["event"] = "runtime.log"
        return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def configure_structured_logging(level: str) -> logging.Logger:
    """为 Runtime 建立单一 JSON handler，不修改宿主 root logger。"""

    logger = logging.getLogger("fashion_ai.runtime")
    logger.setLevel(level.upper())
    logger.propagate = False
    if not any(
        isinstance(handler.formatter, StructuredJsonFormatter)
        for handler in logger.handlers
    ):
        handler = logging.StreamHandler()
        handler.setFormatter(StructuredJsonFormatter())
        logger.addHandler(handler)
    return logger


def current_correlation_id() -> str:
    return _correlation_id.get()


def current_trace_id() -> str | None:
    span_context = trace.get_current_span().get_span_context()
    if not span_context.is_valid:
        return None
    return f"{span_context.trace_id:032x}"


def record_business_context(*, request_id: str | None, run_id: str | None) -> None:
    """仅把稳定业务 ID 放入当前 span，不记录请求正文。"""

    _business_ids.set((request_id, run_id))
    span = trace.get_current_span()
    if request_id:
        span.set_attribute("fashion.request_id", request_id)
    if run_id:
        span.set_attribute("fashion.run_id", run_id)


class _HeaderGetter(Getter[Mapping[str, tuple[str, ...]]]):
    def get(self, carrier: Mapping[str, tuple[str, ...]], key: str) -> list[str] | None:
        values = carrier.get(key.lower())
        return list(values) if values else None

    def keys(self, carrier: Mapping[str, tuple[str, ...]]) -> list[str]:
        return list(carrier.keys())


@dataclass(frozen=True, slots=True)
class TelemetryRuntime:
    """本地 SDK 基座；当前不配置 exporter 或采集端。"""

    tracer: Tracer
    meter: Meter
    request_counter: Counter
    duration_histogram: Histogram
    propagator: TraceContextTextMapPropagator
    exporter_configured: bool = False

    @classmethod
    def create(cls, *, service_name: str) -> "TelemetryRuntime":
        resource = Resource.create({"service.name": service_name})
        tracer_provider = TracerProvider(resource=resource)
        meter_provider = MeterProvider(resource=resource)
        meter = meter_provider.get_meter(service_name)
        return cls(
            tracer=tracer_provider.get_tracer(service_name),
            meter=meter,
            request_counter=meter.create_counter(
                "fashion_ai.http.server.requests",
                unit="{request}",
                description="Runtime 接收的 HTTP 请求数",
            ),
            duration_histogram=meter.create_histogram(
                "fashion_ai.http.server.duration",
                unit="ms",
                description="Runtime HTTP 请求耗时",
            ),
            propagator=TraceContextTextMapPropagator(),
        )


class ObservabilityMiddleware:
    """传播 W3C Trace Context、关联 ID，并记录无正文结构事件。"""

    def __init__(
        self,
        app: ASGIApp,
        *,
        telemetry: TelemetryRuntime,
        logger: logging.Logger,
    ) -> None:
        self._app = app
        self._telemetry = telemetry
        self._logger = logger
        self._getter = _HeaderGetter()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        headers = _headers_from_scope(scope)
        correlation_id = _select_correlation_id(headers)
        token = _correlation_id.set(correlation_id)
        business_token = _business_ids.set((None, None))
        method = str(scope.get("method", "GET")).upper()
        parent_context = self._telemetry.propagator.extract(
            headers, getter=self._getter
        )
        started_at = time.perf_counter()
        response_status = 500

        try:
            with self._telemetry.tracer.start_as_current_span(
                f"HTTP {method}",
                context=parent_context,
                kind=SpanKind.SERVER,
                attributes={
                    "http.request.method": method,
                    "fashion.correlation_id": correlation_id,
                },
            ) as span:
                trace_id = f"{span.get_span_context().trace_id:032x}"

                async def send_with_context(message: Message) -> None:
                    nonlocal response_status
                    if message["type"] == "http.response.start":
                        response_status = int(message["status"])
                        span.set_attribute("http.response.status_code", response_status)
                        response_headers = list(message.get("headers", []))
                        _set_response_header(
                            response_headers,
                            CORRELATION_ID_HEADER,
                            correlation_id,
                        )
                        trace_carrier: dict[str, str] = {}
                        self._telemetry.propagator.inject(trace_carrier)
                        for name, value in trace_carrier.items():
                            _set_response_header(response_headers, name, value)
                        message["headers"] = response_headers
                    await send(message)

                try:
                    await self._app(scope, receive, send_with_context)
                finally:
                    duration_ms = (time.perf_counter() - started_at) * 1000
                    route_template = route_template_label(scope)
                    span.update_name(f"{method} {route_template}")
                    span.set_attribute("http.route", route_template)
                    attributes = {
                        "http.request.method": method,
                        "http.route": route_template,
                        "http.response.status_code": response_status,
                    }
                    self._telemetry.request_counter.add(1, attributes)
                    self._telemetry.duration_histogram.record(duration_ms, attributes)
                    state = _request_state(scope)
                    identity = state.get("fashion_authenticated_service")
                    service_id = (
                        identity.service_id
                        if isinstance(identity, AuthenticatedService)
                        else None
                    )
                    request_size_value = state.get("fashion_request_size", 0)
                    request_size = (
                        request_size_value if isinstance(request_size_value, int) else 0
                    )
                    error_code_value = state.get("fashion_error_code")
                    error_code = (
                        error_code_value if isinstance(error_code_value, str) else None
                    )
                    request_id, run_id = _business_ids.get()
                    if service_id:
                        span.set_attribute("fashion.authenticated_service", service_id)
                    self._logger.info(
                        "runtime.request.completed",
                        extra={
                            "event": "runtime.request.completed",
                            "correlation_id": correlation_id,
                            "trace_id": trace_id,
                            "method": method,
                            "path": route_template,
                            "status_code": response_status,
                            "duration_ms": round(duration_ms, 3),
                            "request_size": request_size,
                            "service_id": service_id,
                            "request_id": request_id,
                            "run_id": run_id,
                            "error_code": error_code,
                        },
                    )
        finally:
            _business_ids.reset(business_token)
            _correlation_id.reset(token)


def _headers_from_scope(scope: Scope) -> dict[str, tuple[str, ...]]:
    grouped: dict[str, list[str]] = {}
    for raw_name, raw_value in scope.get("headers", []):
        name = raw_name.decode("latin-1").lower()
        grouped.setdefault(name, []).append(raw_value.decode("latin-1"))
    return {name: tuple(values) for name, values in grouped.items()}


def _request_state(scope: Scope) -> dict[str, object]:
    value = scope.get("state")
    if isinstance(value, dict):
        return cast(dict[str, object], value)
    return {}


def route_template_label(scope: Scope) -> str:
    """返回有界路由标签；认证前拒绝不得记录攻击者控制的原始 path。"""

    route_path = getattr(scope.get("route"), "path", None)
    if isinstance(route_path, str) and route_path.startswith("/"):
        return route_path[:200]

    request_path = str(scope.get("path", "/"))
    if request_path == "/health":
        return "/health"
    if request_path.startswith("/internal/"):
        return "/internal/*"
    return "/unmatched"


def _select_correlation_id(headers: Mapping[str, tuple[str, ...]]) -> str:
    values = headers.get(CORRELATION_ID_HEADER, ())
    if len(values) == 1:
        candidate = values[0]
        if (
            candidate == candidate.strip()
            and _CORRELATION_ID_PATTERN.fullmatch(candidate) is not None
        ):
            return candidate
    return str(uuid4())


def _set_response_header(
    headers: list[tuple[bytes, bytes]], name: str, value: str
) -> None:
    encoded_name = name.lower().encode("latin-1")
    headers[:] = [(key, item) for key, item in headers if key.lower() != encoded_name]
    headers.append((encoded_name, value.encode("latin-1")))
