"""ASGI 应用入口。"""

import re
from typing import cast
from uuid import UUID

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from fashion_ai.api import create_router
from fashion_ai.api.middleware import RequestSecurityMiddleware
from fashion_ai.api.security import (
    HmacServiceAuthenticator,
    HmacServiceRequestSigner,
    InMemoryNonceStore,
)
from fashion_ai.application import (
    DisabledProductAttributeSuggester,
    DisabledRequirementAnalyzer,
    DisabledSelectionRanker,
    ProductAttributeSuggester,
    RequirementAnalyzer,
    SelectionRanker,
    SelectionStylingWorkflow,
)
from fashion_ai.application.demo_provider import (
    DemoProductAttributeSuggester,
    DemoRequirementAnalyzer,
    DemoSelectionRanker,
)
from fashion_ai.domain.models import ErrorDetail, ErrorEnvelope
from fashion_ai.ports.service_auth import ServiceAuthenticator
from fashion_ai.settings import (
    SERVICE_NAME,
    SERVICE_VERSION,
    ProviderMode,
    RuntimeSettings,
)
from fashion_ai.telemetry import (
    ObservabilityMiddleware,
    TelemetryRuntime,
    configure_structured_logging,
    current_correlation_id,
    current_trace_id,
    record_business_context,
)

_STABLE_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")


def _safe_request_id(body: object) -> UUID | None:
    if not isinstance(body, dict):
        return None
    candidate = cast(dict[object, object], body).get("request_id")
    if not isinstance(candidate, str):
        return None
    try:
        return UUID(candidate)
    except (ValueError, AttributeError):
        return None


def _safe_run_id(body: object) -> str | None:
    if not isinstance(body, dict):
        return None
    value = cast(dict[object, object], body).get("run_id")
    if not isinstance(value, str) or _STABLE_ID_PATTERN.fullmatch(value) is None:
        return None
    return value


def create_app(
    *,
    settings: RuntimeSettings | None = None,
    analyzer: RequirementAnalyzer | None = None,
    product_suggester: ProductAttributeSuggester | None = None,
    selection_ranker: SelectionRanker | None = None,
    authenticator: ServiceAuthenticator | None = None,
    telemetry: TelemetryRuntime | None = None,
) -> FastAPI:
    """创建运行时应用；默认 disabled，显式 demo 时使用本地规则。"""

    runtime_settings = settings or RuntimeSettings.from_env()
    demo_mode = runtime_settings.provider_mode is ProviderMode.DEMO
    runtime_logger = configure_structured_logging(runtime_settings.log_level)
    runtime_telemetry = telemetry or TelemetryRuntime.create(service_name=SERVICE_NAME)
    runtime_authenticator = authenticator or HmacServiceAuthenticator(
        settings=runtime_settings.service_auth,
        nonce_store=InMemoryNonceStore(max_entries=runtime_settings.max_nonce_entries),
    )
    runtime_signer = HmacServiceRequestSigner(
        active_key=runtime_settings.service_auth.active_key,
    )

    app = FastAPI(
        title="Fashion AI Runtime Internal API",
        version=SERVICE_VERSION,
        description="AI 智能选品内部 Python 运行时；demo 仅用于本机试用。",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation_error(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        request_id = _safe_request_id(exc.body)
        run_id = _safe_run_id(exc.body)
        record_business_context(
            request_id=str(request_id) if request_id else None,
            run_id=run_id,
        )
        request.scope.setdefault("state", {})["fashion_error_code"] = (
            "REQUEST_VALIDATION_FAILED"
        )
        error = ErrorEnvelope(
            version="1.0",
            request_id=request_id,
            correlation_id=current_correlation_id() or None,
            run_id=run_id,
            trace_id=current_trace_id(),
            error=ErrorDetail(
                code="REQUEST_VALIDATION_FAILED",
                message="请求未通过契约校验",
                retryable=False,
            ),
        )
        return JSONResponse(status_code=422, content=error.model_dump(mode="json"))

    app.include_router(
        create_router(
            analyzer
            or (
                DemoRequirementAnalyzer()
                if demo_mode
                else DisabledRequirementAnalyzer()
            ),
            product_suggester or (
                DemoProductAttributeSuggester()
                if demo_mode
                else DisabledProductAttributeSuggester()
            ),
            SelectionStylingWorkflow(
                selection_ranker
                or (DemoSelectionRanker() if demo_mode else DisabledSelectionRanker())
            ),
            demo_mode=demo_mode,
        )
    )
    app.add_middleware(
        RequestSecurityMiddleware,
        max_body_bytes=runtime_settings.max_json_body_bytes,
        authenticator=runtime_authenticator,
    )
    app.add_middleware(
        ObservabilityMiddleware,
        telemetry=runtime_telemetry,
        logger=runtime_logger,
    )
    app.state.service_name = SERVICE_NAME
    app.state.settings = runtime_settings
    app.state.authenticator = runtime_authenticator
    app.state.service_request_signer = runtime_signer
    app.state.telemetry = runtime_telemetry
    return app


app = create_app()
