package com.ruoyi.aden.api.common;

import com.ruoyi.aden.application.error.AdenApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenApiExceptionHandlerTest {
    private static final String CORRELATION = "77777777-7777-4777-8777-777777777777";
    private final AdenApiExceptionHandler handler = new AdenApiExceptionHandler();

    @Test
    void rateLimitUsesReal429RetryAfterAndRetryableEnvelope() {
        var response = handler.applicationFailure(
                new AdenApplicationException("ADEN_RATE_LIMITED", "SSE 连接数已达到上限"), request());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(CORRELATION, response.getHeaders().getFirst(AdenCorrelationIdFilter.HEADER));
        assertTrue(response.getBody().retryable());
        assertEquals("ADEN_RATE_LIMITED", response.getBody().errorCode());
    }

    @Test
    void expiredCursorAndVersionConflictKeepStableStatusWithoutSensitiveDetails() {
        var expired = handler.applicationFailure(new AdenApplicationException(
                "ADEN_STREAM_CURSOR_EXPIRED", "游标过期", Map.of("snapshotPath", "/safe/bootstrap")), request());
        var conflict = handler.applicationFailure(new AdenApplicationException(
                "ADEN_VERSION_CONFLICT", "版本冲突", Map.of("currentVersion", "9223372036854775807")), request());

        assertEquals(HttpStatus.GONE, expired.getStatusCode());
        assertEquals("/safe/bootstrap", expired.getBody().details().get("snapshotPath"));
        assertFalse(expired.getBody().retryable());
        assertEquals(HttpStatus.PRECONDITION_FAILED, conflict.getStatusCode());
        assertEquals("9223372036854775807", conflict.getBody().details().get("currentVersion"));
        assertFalse(conflict.getBody().retryable());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdenCorrelationIdFilter.REQUEST_ATTRIBUTE, CORRELATION);
        return request;
    }
}
