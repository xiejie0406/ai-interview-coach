package com.ruoyi.aden.api.common;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenSecurityBoundaryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void anyOriginIncludingPreflightIsRejectedBeforeCorsHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/aden/workspaces");
        request.addHeader("Origin", "https://attacker.example");
        request.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        AdenCorrelationIdFilter correlation = new AdenCorrelationIdFilter();
        AdenOriginGuardFilter origin = new AdenOriginGuardFilter(new AdenApiErrorWriter(objectMapper));

        correlation.doFilter(request, response,
                (correlatedRequest, correlatedResponse) -> origin.doFilter(
                        correlatedRequest, correlatedResponse, (ignoredRequest, ignoredResponse) -> invoked.set(true)));

        assertFalse(invoked.get());
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals("ADEN_PERMISSION_DENIED", body.path("errorCode").asText());
        assertTrue(body.has("data") && body.get("data").isNull());
        assertEquals(response.getHeader(AdenCorrelationIdFilter.HEADER), body.path("correlationId").asText());
    }

    @Test
    void originlessElectronMainRequestContinues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/aden/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new AdenOriginGuardFilter(new AdenApiErrorWriter(objectMapper)).doFilter(
                request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void invalidCorrelationHeaderIsNotReflected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/aden/workspaces");
        request.addHeader(AdenCorrelationIdFilter.HEADER, "not-a-uuid\r\ninjected: true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new AdenCorrelationIdFilter().doFilter(
                request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertNotEquals("not-a-uuid\r\ninjected: true", response.getHeader(AdenCorrelationIdFilter.HEADER));
        assertEquals(36, response.getHeader(AdenCorrelationIdFilter.HEADER).length());
    }
}
