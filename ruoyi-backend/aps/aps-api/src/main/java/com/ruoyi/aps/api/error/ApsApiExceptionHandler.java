package com.ruoyi.aps.api.error;

import java.util.UUID;
import com.ruoyi.aps.api.dto.ApsProblem;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * APS 错误统一映射；响应不泄漏堆栈、SQL 或内部异常文本。
 */
@RestControllerAdvice(basePackages = "com.ruoyi.aps.api")
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
public class ApsApiExceptionHandler
{
    @ExceptionHandler(ApsBusinessException.class)
    public ResponseEntity<ApsProblem> handleBusiness(ApsBusinessException exception)
    {
        HttpStatus status = statusOf(exception.errorCode());
        String reasonCode = reasonCodeOf(exception.errorCode());
        ApsProblem problem = exception instanceof ApsValidationException validation
                ? ApsProblem.validation(UUID.randomUUID().toString(), reasonCode, status.getReasonPhrase(),
                        exception.getMessage(), validation.issues())
                : ApsProblem.error(UUID.randomUUID().toString(), reasonCode, status.getReasonPhrase(),
                        exception.getMessage(), exception.errorCode() == ApsErrorCode.PERSISTENCE_UNAVAILABLE);
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class})
    public ResponseEntity<ApsProblem> handleInvalidHttpInput(Exception exception)
    {
        return ResponseEntity.badRequest().body(ApsProblem.error(
                UUID.randomUUID().toString(), "CONTRACT_VALIDATION_FAILED", "Bad Request",
                "请求字段、枚举或时间格式无效", false));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApsProblem> handleDataConflict(DataIntegrityViolationException exception)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApsProblem.error(
                UUID.randomUUID().toString(), "CONFLICT", "Conflict",
                "数据与已有编码、关联关系或数据库约束冲突", false));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApsProblem> handleUnexpected(Exception exception)
    {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApsProblem.error(
                UUID.randomUUID().toString(), "INTERNAL_ERROR", "Internal Server Error",
                "请求处理失败，请使用 problemId 查询服务端日志", false));
    }

    private HttpStatus statusOf(ApsErrorCode code)
    {
        return switch (code)
        {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, IDEMPOTENCY_CONFLICT, STALE_VERSION, INVALID_EXECUTION_TRANSITION,
                    EXECUTION_CONFLICT, QUANTITY_BALANCE_VIOLATION, INSUFFICIENT_QUANTITY,
                    QUALITY_DISPOSITION_REQUIRED, EXECUTION_INPUT_STALE, UNSUPPORTED_SYNC_RULE -> HttpStatus.CONFLICT;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case PERSISTENCE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String reasonCodeOf(ApsErrorCode code)
    {
        return switch (code)
        {
            case INVALID_REQUEST -> "CONTRACT_VALIDATION_FAILED";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case IDEMPOTENCY_CONFLICT -> "IDEMPOTENCY_CONFLICT";
            case STALE_VERSION -> "STALE_VERSION";
            case INVALID_EXECUTION_TRANSITION -> "INVALID_EXECUTION_TRANSITION";
            case EXECUTION_CONFLICT -> "EXECUTION_CONFLICT";
            case QUANTITY_BALANCE_VIOLATION -> "QUANTITY_BALANCE_VIOLATION";
            case INSUFFICIENT_QUANTITY -> "INSUFFICIENT_QUANTITY";
            case QUALITY_DISPOSITION_REQUIRED -> "QUALITY_DISPOSITION_REQUIRED";
            case EXECUTION_INPUT_STALE -> "EXECUTION_INPUT_STALE";
            case UNSUPPORTED_SYNC_RULE -> "UNSUPPORTED_SYNC_RULE";
            case PERSISTENCE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            case INTERNAL_ERROR -> "INTERNAL_ERROR";
        };
    }
}
