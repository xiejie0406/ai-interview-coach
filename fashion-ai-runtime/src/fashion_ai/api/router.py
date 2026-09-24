"""FastAPI 路由与服务认证契约声明。"""

import asyncio
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Header, Request, Security
from fastapi.responses import JSONResponse
from fastapi.security import APIKeyHeader

from fashion_ai.api.security import (
    KEY_ID_PATTERN,
    NONCE_PATTERN,
    REQUEST_ID_PATTERN,
    SERVICE_ID_PATTERN,
    SHA256_PATTERN,
    TIMESTAMP_PATTERN,
    TRACEPARENT_PATTERN,
)
from fashion_ai.application import (
    ProductAttributeSuggester,
    ProviderDisabledError,
    RequirementAnalyzer,
    SelectionRanker,
)
from fashion_ai.domain.models import (
    CapabilitiesResponse,
    CapabilityOperation,
    ErrorDetail,
    ErrorEnvelope,
    HealthResponse,
    ImageGenerationRequest,
    ProductAttributeSuggestionRequest,
    ProductAttributeSuggestionResponse,
    ProviderCapability,
    RequirementAnalysisRequest,
    RequirementAnalysisResponse,
    SelectionStylingRequest,
    SelectionStylingResponse,
)
from fashion_ai.ports.service_auth import AuthenticatedService
from fashion_ai.telemetry import (
    current_correlation_id,
    current_trace_id,
    record_business_context,
)

MAX_ANALYSIS_SECONDS = 120.0

_HMAC_DESCRIPTION = (
    "Java→Python HMAC-SHA256 v1。除签名头外还必须携带 "
    "X-Fashion-Service-Id、X-Fashion-Key-Id、X-Fashion-Timestamp、"
    "X-Fashion-Nonce、X-Fashion-Audience、X-Fashion-Content-SHA256、"
    "X-Request-Id、X-Correlation-Id 和 traceparent；tracestate 可选。"
    "Canonical 精确为 `FASHION-HMAC-SHA256\\n{METHOD_UPPER}\\n"
    "{RAW_PATH}\\n{SERVICE_ID}\\n{AUDIENCE}\\n{TIMESTAMP_UNIX_SECONDS}\\n"
    "{NONCE}\\n{LOWERCASE_BODY_SHA256}`，末尾无换行；当前禁止 query。"
)
_signature_scheme = APIKeyHeader(
    name="X-Fashion-Signature",
    scheme_name="FashionHmacV1",
    description=_HMAC_DESCRIPTION,
)


async def require_service_auth_contract(
    signature: Annotated[str, Security(_signature_scheme)],
    service_id: Annotated[
        str,
        Header(alias="X-Fashion-Service-Id", pattern=SERVICE_ID_PATTERN),
    ],
    key_id: Annotated[
        str,
        Header(alias="X-Fashion-Key-Id", pattern=KEY_ID_PATTERN),
    ],
    timestamp: Annotated[
        str,
        Header(alias="X-Fashion-Timestamp", pattern=TIMESTAMP_PATTERN),
    ],
    nonce: Annotated[
        str,
        Header(alias="X-Fashion-Nonce", pattern=NONCE_PATTERN),
    ],
    audience: Annotated[
        str,
        Header(alias="X-Fashion-Audience", pattern=SERVICE_ID_PATTERN),
    ],
    content_sha256: Annotated[
        str,
        Header(alias="X-Fashion-Content-SHA256", pattern=SHA256_PATTERN),
    ],
    request_id_header: Annotated[
        str,
        Header(alias="X-Request-Id", pattern=REQUEST_ID_PATTERN),
    ],
    correlation_id: Annotated[
        str,
        Header(alias="X-Correlation-Id", pattern=SERVICE_ID_PATTERN),
    ],
    traceparent: Annotated[
        str,
        Header(alias="traceparent", pattern=TRACEPARENT_PATTERN),
    ],
    tracestate: Annotated[
        str | None,
        Header(alias="tracestate", max_length=512),
    ] = None,
) -> None:
    """只生成 OpenAPI 参数；真实校验已在读取 JSON 前由 ASGI 中间件完成。"""

    del (
        signature,
        service_id,
        key_id,
        timestamp,
        nonce,
        audience,
        content_sha256,
        request_id_header,
        correlation_id,
        traceparent,
        tracestate,
    )


def create_router(
    analyzer: RequirementAnalyzer,
    product_suggester: ProductAttributeSuggester,
    selection_ranker: SelectionRanker,
) -> APIRouter:
    router = APIRouter()

    @router.get(
        "/health",
        operation_id="getHealth",
        response_model=HealthResponse,
        tags=["runtime"],
    )
    async def get_health() -> HealthResponse:
        return HealthResponse(
            version="1.0",
            service="fashion-ai-runtime",
            service_version="0.1.0",
            status="ok",
            provider=ProviderCapability(mode="disabled", enabled=False),
        )

    @router.get(
        "/internal/v1/capabilities",
        operation_id="getCapabilities",
        response_model=CapabilitiesResponse,
        dependencies=[Security(require_service_auth_contract)],
        responses={
            401: {
                "model": ErrorEnvelope,
                "description": "SERVICE_AUTHENTICATION_FAILED",
            },
            403: {
                "model": ErrorEnvelope,
                "description": "SERVICE_NOT_AUTHORIZED",
            },
            429: {"model": ErrorEnvelope, "description": "RATE_LIMITED"},
            422: {
                "model": ErrorEnvelope,
                "description": "REQUEST_VALIDATION_FAILED",
            },
        },
        tags=["internal"],
    )
    async def get_capabilities() -> CapabilitiesResponse:
        return CapabilitiesResponse(
            version="1.0",
            service="fashion-ai-runtime",
            provider=ProviderCapability(mode="disabled", enabled=False),
            operations=(
                CapabilityOperation(
                    name="requirement-analysis",
                    versions=("1.0",),
                    status="unavailable",
                    reason="provider_disabled",
                ),
                CapabilityOperation(
                    name="product-attribute-suggestion",
                    versions=("1.0",),
                    status="unavailable",
                    reason="provider_disabled",
                ),
                CapabilityOperation(
                    name="selection-styling",
                    versions=("1.0",),
                    status="unavailable",
                    reason="provider_disabled",
                ),
                CapabilityOperation(
                    name="image-generation",
                    versions=("1.0",),
                    status="unavailable",
                    reason="provider_disabled",
                ),
            ),
        )

    @router.post(
        "/internal/v1/image-generation/submit",
        operation_id="submitImageGeneration",
        dependencies=[Security(require_service_auth_contract)],
        responses={
            401: {
                "model": ErrorEnvelope,
                "description": "SERVICE_AUTHENTICATION_FAILED",
            },
            403: {"model": ErrorEnvelope, "description": "SERVICE_NOT_AUTHORIZED"},
            408: {"model": ErrorEnvelope, "description": "调用期限已过"},
            413: {"model": ErrorEnvelope, "description": "PAYLOAD_TOO_LARGE"},
            422: {"model": ErrorEnvelope, "description": "图片输入未通过隐私契约"},
            503: {"model": ErrorEnvelope, "description": "图片 Provider 未启用"},
        },
        tags=["internal"],
    )
    async def submit_image_generation(
        command: ImageGenerationRequest,
        request: Request,
    ) -> JSONResponse:
        """当前只固定安全契约；真实适配器必须等生产试运行显式启用。"""

        record_business_context(
            request_id=str(command.request_id), run_id=command.run_id
        )
        identity = getattr(request.state, "fashion_authenticated_service", None)
        if (
            not isinstance(identity, AuthenticatedService)
            or identity.request_id != str(command.request_id)
            or (
                command.correlation_id is not None
                and identity.correlation_id != command.correlation_id
            )
        ):
            request.state.fashion_error_code = "REQUEST_VALIDATION_FAILED"
            code = "REQUEST_VALIDATION_FAILED"
            message = "请求标识与认证请求头不一致"
            status = 422
        elif command.deadline_at <= datetime.now(UTC):
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            code = "DEADLINE_EXCEEDED"
            message = "请求 deadline_at 已过期"
            status = 408
        else:
            request.state.fashion_error_code = "PROVIDER_DISABLED"
            code = "PROVIDER_DISABLED"
            message = "图片 Provider 未启用，未提交外部任务"
            status = 503
        error = ErrorEnvelope(
            version="1.0",
            request_id=command.request_id,
            correlation_id=current_correlation_id() or None,
            run_id=command.run_id,
            trace_id=current_trace_id(),
            error=ErrorDetail(code=code, message=message, retryable=False),
        )
        return JSONResponse(status_code=status, content=error.model_dump(mode="json"))

    @router.post(
        "/internal/v1/requirement-analysis",
        operation_id="analyzeRequirements",
        response_model=RequirementAnalysisResponse,
        dependencies=[Security(require_service_auth_contract)],
        responses={
            401: {
                "model": ErrorEnvelope,
                "description": "SERVICE_AUTHENTICATION_FAILED",
            },
            403: {
                "model": ErrorEnvelope,
                "description": "SERVICE_NOT_AUTHORIZED",
            },
            408: {"model": ErrorEnvelope, "description": "调用期限已过"},
            413: {"model": ErrorEnvelope, "description": "PAYLOAD_TOO_LARGE"},
            422: {"model": ErrorEnvelope, "description": "请求未通过契约校验"},
            429: {"model": ErrorEnvelope, "description": "RATE_LIMITED"},
            503: {"model": ErrorEnvelope, "description": "AI Provider 未启用"},
        },
        tags=["internal"],
    )
    async def analyze_requirements(
        command: RequirementAnalysisRequest,
        request: Request,
    ) -> RequirementAnalysisResponse | JSONResponse:
        record_business_context(
            request_id=str(command.request_id), run_id=command.run_id
        )
        identity = getattr(
            request.state,
            "fashion_authenticated_service",
            None,
        )
        if (
            not isinstance(identity, AuthenticatedService)
            or identity.request_id != str(command.request_id)
            or (
                command.correlation_id is not None
                and identity.correlation_id != command.correlation_id
            )
        ):
            request.state.fashion_error_code = "REQUEST_VALIDATION_FAILED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="REQUEST_VALIDATION_FAILED",
                    message="请求标识与认证请求头不一致",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=422, content=error.model_dump(mode="json"))
        now = datetime.now(UTC)
        if command.deadline_at <= now:
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="DEADLINE_EXCEEDED",
                    message="请求 deadline_at 已过期",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=408, content=error.model_dump(mode="json"))

        timeout_seconds = min(
            (command.deadline_at - now).total_seconds(), MAX_ANALYSIS_SECONDS
        )
        try:
            async with asyncio.timeout(timeout_seconds):
                result = await analyzer.analyze(command)
        except TimeoutError:
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="DEADLINE_EXCEEDED",
                    message="需求分析超过执行期限",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=408, content=error.model_dump(mode="json"))
        except ProviderDisabledError:
            request.state.fashion_error_code = "PROVIDER_DISABLED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="PROVIDER_DISABLED",
                    message="AI Provider 未启用，需求分析暂不可用",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=503, content=error.model_dump(mode="json"))

        return RequirementAnalysisResponse(
            version="1.0",
            request_id=command.request_id,
            run_id=command.run_id,
            result=result,
        )

    @router.post(
        "/internal/v1/product-attribute-suggestion",
        operation_id="suggestProductAttributes",
        response_model=ProductAttributeSuggestionResponse,
        dependencies=[Security(require_service_auth_contract)],
        responses={
            401: {
                "model": ErrorEnvelope,
                "description": "SERVICE_AUTHENTICATION_FAILED",
            },
            403: {"model": ErrorEnvelope, "description": "SERVICE_NOT_AUTHORIZED"},
            408: {"model": ErrorEnvelope, "description": "调用期限已过"},
            413: {"model": ErrorEnvelope, "description": "PAYLOAD_TOO_LARGE"},
            422: {"model": ErrorEnvelope, "description": "请求未通过契约校验"},
            429: {"model": ErrorEnvelope, "description": "RATE_LIMITED"},
            503: {"model": ErrorEnvelope, "description": "AI Provider 未启用"},
        },
        tags=["internal"],
    )
    async def suggest_product_attributes(
        command: ProductAttributeSuggestionRequest,
        request: Request,
    ) -> ProductAttributeSuggestionResponse | JSONResponse:
        record_business_context(
            request_id=str(command.request_id), run_id=command.run_id
        )
        identity = getattr(request.state, "fashion_authenticated_service", None)
        if (
            not isinstance(identity, AuthenticatedService)
            or identity.request_id != str(command.request_id)
            or (
                command.correlation_id is not None
                and identity.correlation_id != command.correlation_id
            )
        ):
            request.state.fashion_error_code = "REQUEST_VALIDATION_FAILED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="REQUEST_VALIDATION_FAILED",
                    message="请求标识与认证请求头不一致",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=422, content=error.model_dump(mode="json"))
        now = datetime.now(UTC)
        if command.deadline_at <= now:
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="DEADLINE_EXCEEDED",
                    message="请求 deadline_at 已过期",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=408, content=error.model_dump(mode="json"))
        try:
            async with asyncio.timeout(
                min((command.deadline_at - now).total_seconds(), MAX_ANALYSIS_SECONDS)
            ):
                result = await product_suggester.suggest(command)
        except TimeoutError:
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="DEADLINE_EXCEEDED",
                    message="商品属性建议超过执行期限",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=408, content=error.model_dump(mode="json"))
        except ProviderDisabledError:
            request.state.fashion_error_code = "PROVIDER_DISABLED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="PROVIDER_DISABLED",
                    message="AI Provider 未启用，商品属性建议暂不可用",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=503, content=error.model_dump(mode="json"))
        return ProductAttributeSuggestionResponse(
            version="1.0",
            request_id=command.request_id,
            run_id=command.run_id,
            result=result,
        )

    @router.post(
        "/internal/v1/selection-styling",
        operation_id="rankSelectionStyling",
        response_model=SelectionStylingResponse,
        dependencies=[Security(require_service_auth_contract)],
        responses={
            401: {
                "model": ErrorEnvelope,
                "description": "SERVICE_AUTHENTICATION_FAILED",
            },
            403: {"model": ErrorEnvelope, "description": "SERVICE_NOT_AUTHORIZED"},
            408: {"model": ErrorEnvelope, "description": "调用期限已过"},
            413: {"model": ErrorEnvelope, "description": "PAYLOAD_TOO_LARGE"},
            422: {"model": ErrorEnvelope, "description": "候选或输出未通过契约校验"},
            429: {"model": ErrorEnvelope, "description": "RATE_LIMITED"},
            503: {"model": ErrorEnvelope, "description": "AI Provider 未启用"},
        },
        tags=["internal"],
    )
    async def rank_selection_styling(
        command: SelectionStylingRequest,
        request: Request,
    ) -> SelectionStylingResponse | JSONResponse:
        record_business_context(
            request_id=str(command.request_id), run_id=command.run_id
        )
        identity = getattr(request.state, "fashion_authenticated_service", None)
        if (
            not isinstance(identity, AuthenticatedService)
            or identity.request_id != str(command.request_id)
            or (
                command.correlation_id is not None
                and identity.correlation_id != command.correlation_id
            )
        ):
            request.state.fashion_error_code = "REQUEST_VALIDATION_FAILED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="REQUEST_VALIDATION_FAILED",
                    message="请求标识与认证请求头不一致",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=422, content=error.model_dump(mode="json"))
        now = datetime.now(UTC)
        if command.deadline_at <= now:
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="DEADLINE_EXCEEDED",
                    message="请求 deadline_at 已过期",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=408, content=error.model_dump(mode="json"))
        try:
            async with asyncio.timeout(
                min((command.deadline_at - now).total_seconds(), MAX_ANALYSIS_SECONDS)
            ):
                result = await selection_ranker.rank(command)
        except TimeoutError:
            request.state.fashion_error_code = "DEADLINE_EXCEEDED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="DEADLINE_EXCEEDED",
                    message="选品搭配超过执行期限",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=408, content=error.model_dump(mode="json"))
        except ProviderDisabledError:
            request.state.fashion_error_code = "PROVIDER_DISABLED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="PROVIDER_DISABLED",
                    message="AI Provider 未启用，选品搭配暂不可用",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=503, content=error.model_dump(mode="json"))
        except ValueError:
            request.state.fashion_error_code = "REQUEST_VALIDATION_FAILED"
            error = ErrorEnvelope(
                version="1.0",
                request_id=command.request_id,
                correlation_id=current_correlation_id() or None,
                run_id=command.run_id,
                trace_id=current_trace_id(),
                error=ErrorDetail(
                    code="REQUEST_VALIDATION_FAILED",
                    message="选品结果未通过冻结候选护栏",
                    retryable=False,
                ),
            )
            return JSONResponse(status_code=422, content=error.model_dump(mode="json"))
        return SelectionStylingResponse(
            version="1.0",
            request_id=command.request_id,
            run_id=command.run_id,
            result=result,
        )

    return router
