package com.aiinterviewcoach.adapters.inbound.rest.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 为未知路由等 Servlet 容器错误提供最后一道统一 JSON 映射。 */
@RestController
public final class ApiFallbackErrorController implements ErrorController {
    private static final Logger LOG = LoggerFactory.getLogger(ApiFallbackErrorController.class);

    @RequestMapping(path = "${server.error.path:/error}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErrorEnvelope> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String correlationId = CorrelationIdFilter.correlationId(request);
        ErrorDescriptor descriptor = descriptor(status);

        if (status.is5xxServerError()) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            String exceptionType = exception == null ? "unknown" : exception.getClass().getName();
            LOG.error("Servlet error correlationId={} status={} exceptionType={}",
                    correlationId, status.value(), exceptionType);
        }

        ErrorEnvelope envelope = new ErrorEnvelope(new ApiError(
                descriptor.code(),
                descriptor.message(),
                descriptor.retryable(),
                correlationId,
                Map.of()));
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(envelope);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer value) {
            HttpStatus status = HttpStatus.resolve(value);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.NOT_FOUND;
    }

    private ErrorDescriptor descriptor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> new ErrorDescriptor("VALIDATION_FAILED", "请求参数不合法", false);
            case UNAUTHORIZED -> new ErrorDescriptor("AUTH_REQUIRED", "请先登录后再继续", false);
            case FORBIDDEN -> new ErrorDescriptor("FORBIDDEN", "当前账号无权执行此操作", false);
            case NOT_FOUND -> new ErrorDescriptor("NOT_FOUND", "请求的资源不存在", false);
            case METHOD_NOT_ALLOWED -> new ErrorDescriptor("METHOD_NOT_ALLOWED", "请求方法不受支持", false);
            case UNSUPPORTED_MEDIA_TYPE -> new ErrorDescriptor("UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持", false);
            case TOO_MANY_REQUESTS -> new ErrorDescriptor("RATE_LIMITED", "请求过于频繁，请稍后重试", true);
            default -> new ErrorDescriptor("INTERNAL_ERROR", "服务暂时不可用", status.is5xxServerError());
        };
    }

    private record ErrorDescriptor(String code, String message, boolean retryable) {
    }
}
