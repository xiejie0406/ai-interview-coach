package com.ruoyi.aden.infrastructure.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.api.common.AdenApiErrorWriter;
import com.ruoyi.aden.application.runner.AdenRunnerAuthenticationPort;
import com.ruoyi.aden.application.runner.AdenRunnerCredentialPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerSessionPrincipal;
import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenRunnerAuthenticationFilterTest {
    private static final Instant NOW = Instant.parse("2026-09-13T04:30:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void sessionExchangeAcceptsOnlyCredentialScheme() throws Exception {
        StubPort port = new StubPort();
        var valid = request("POST", "/api/v1/aden/runner/v1/sessions",
                "AdenCredential credential-token");
        AtomicReference<Object> principal = new AtomicReference<>();

        filter(port).doFilter(valid, new MockHttpServletResponse(),
                (request, response) -> principal.set(
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal()));

        assertTrue(principal.get() instanceof AdenRunnerCredentialPrincipal);
        assertEquals(1, port.credentialCalls);
        assertEquals(0, port.sessionCalls);
    }

    @Test
    void deliveryPathRejectsJwtAndCredentialWithoutTryingAnotherProvider() throws Exception {
        for (String authorization : new String[]{"Bearer jwt", "AdenCredential credential-token"}) {
            StubPort port = new StubPort();
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();
            filter(port).doFilter(request("POST", "/api/v1/aden/runner/v1/deliveries:claim", authorization),
                    response, (request, ignored) -> invoked.set(true));
            assertFalse(invoked.get());
            assertEquals(401, response.getStatus());
            JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
            assertEquals("ADEN_RUNNER_AUTH_INVALID", body.path("errorCode").asText());
            assertEquals(0, port.credentialCalls + port.sessionCalls);
        }
    }

    @Test
    void deliveryPathAuthenticatesOnlyActiveSessionToken() throws Exception {
        StubPort port = new StubPort();
        AtomicReference<Object> principal = new AtomicReference<>();
        filter(port).doFilter(request("POST", "/api/v1/aden/runner/v1/deliveries:claim",
                        "AdenRunner session-token"), new MockHttpServletResponse(),
                (request, response) -> principal.set(
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
        assertTrue(principal.get() instanceof AdenRunnerSessionPrincipal);
        assertEquals(0, port.credentialCalls);
        assertEquals(1, port.sessionCalls);
    }

    private AdenRunnerAuthenticationFilter filter(StubPort port) {
        return new AdenRunnerAuthenticationFilter(port, new AdenApiErrorWriter(objectMapper),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockHttpServletRequest request(String method, String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", authorization);
        return request;
    }

    private static final class StubPort implements AdenRunnerAuthenticationPort {
        private int credentialCalls;
        private int sessionCalls;

        @Override
        public Optional<AdenRunnerCredentialPrincipal> authenticateCredential(String token, Instant now) {
            credentialCalls++;
            return "credential-token".equals(token) ? Optional.of(new AdenRunnerCredentialPrincipal(
                    workspace(), runner(), credential(), 1)) : Optional.empty();
        }

        @Override
        public Optional<AdenRunnerSessionPrincipal> authenticateSession(String token, Instant now) {
            sessionCalls++;
            return "session-token".equals(token) ? Optional.of(new AdenRunnerSessionPrincipal(
                    workspace(), runner(), session(), 1)) : Optional.empty();
        }

        private static AdenWorkspaceId workspace() { return new AdenWorkspaceId(uuid(1)); }
        private static AdenRunnerId runner() { return new AdenRunnerId(uuid(2)); }
        private static AdenCredentialId credential() { return new AdenCredentialId(uuid(3)); }
        private static AdenSessionId session() { return new AdenSessionId(uuid(4)); }
        private static String uuid(int value) {
            return String.format("00000000-0000-4000-8000-%012d", value);
        }
    }
}
