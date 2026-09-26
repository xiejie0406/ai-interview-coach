package com.ruoyi.aden.api.runner;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.application.runner.AdenRunnerClaimService;
import com.ruoyi.aden.application.runner.AdenRunnerCredentialPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerDeliveryRepository;
import com.ruoyi.aden.application.runner.AdenRunnerHeartbeatService;
import com.ruoyi.aden.application.runner.AdenRunnerReceiptService;
import com.ruoyi.aden.application.runner.AdenRunnerSessionPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerSessionService;
import com.ruoyi.aden.configuration.AdenProperties;
import com.ruoyi.aden.domain.runner.AdenCredentialId;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 锁定 Java Runner HTTP 适配器与 canonical Runner v1 契约的关键线级语义。 */
class AdenRunnerControllerContractTest {
    private static final String WORKSPACE = "11111111-1111-4111-8111-111111111111";
    private static final String RUNNER = "44444444-4444-4444-8444-444444444444";
    private static final String CREDENTIAL = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String SESSION = "55555555-5555-4555-8555-555555555555";
    private static final String DELIVERY = "66666666-6666-4666-8666-666666666666";
    private static final String TASK = "22222222-2222-4222-8222-222222222222";
    private static final String STEP = "33333333-3333-4333-8333-333333333333";
    private static final String CLAIM = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
    private static final String RECEIPT = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    private static final String CORRELATION = "77777777-7777-4777-8777-777777777777";
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    private AdenRunnerSessionService sessions;
    private AdenRunnerClaimService claims;
    private AdenRunnerHeartbeatService heartbeats;
    private AdenRunnerReceiptService receipts;
    private AdenRunnerController controller;

    @BeforeEach
    void createController() {
        sessions = mock(AdenRunnerSessionService.class);
        claims = mock(AdenRunnerClaimService.class);
        heartbeats = mock(AdenRunnerHeartbeatService.class);
        receipts = mock(AdenRunnerReceiptService.class);
        controller = new AdenRunnerController(sessions, claims, heartbeats, receipts,
                new ObjectMapper(), new AdenProperties());
    }

    @Test
    void routeSurfaceExactlyMatchesRunnerV1AndDoesNotExposeLegacyAliases() {
        RequestMapping root = AdenRunnerController.class.getAnnotation(RequestMapping.class);
        assertEquals(List.of("/api/v1/aden/runner/v1"), List.of(root.value()));

        Set<String> mappings = java.util.Arrays.stream(AdenRunnerController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(method -> method.getAnnotation(PostMapping.class).value()[0])
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "/sessions",
                "/sessions/{sessionId}/heartbeats",
                "/deliveries:claim",
                "/deliveries/{deliveryId}/heartbeats",
                "/deliveries/{deliveryId}/receipts"), mappings);
        assertFalse(mappings.contains("/claims"));
        assertFalse(mappings.contains("/heartbeat"));
        assertFalse(mappings.contains("/receipts"));
    }

    @Test
    void sessionResponseUses201NoStoreAndCanonicalInt64String() {
        when(sessions.exchange(any(), any(), any())).thenReturn(new AdenRunnerSessionService.SessionExchange(
                workspaceId(), runnerId(), sessionId(), "a".repeat(64) + "." + "b".repeat(64),
                Long.MAX_VALUE, NOW.plusSeconds(900), NOW));
        var request = requestWithCorrelation();

        var response = controller.exchange(new AdenRunnerController.SessionRequest(
                1, "1.0.0", 1, Set.of(AdenCapabilityCode.CORE)), credentialAuthentication(), request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
        assertEquals("9223372036854775807", response.getBody().sessionEpoch());
        assertEquals("ACTIVE", response.getBody().state());
        assertEquals(15, response.getBody().heartbeatAfterSeconds());
    }

    @Test
    void claimKeepsHeaderIdempotencySeparateAndSerializesFenceWithoutPrecisionLoss() {
        String packageHash = "3".repeat(64);
        String taskPackage = "{\"schemaVersion\":1,\"taskId\":\"" + TASK
                + "\",\"stepId\":\"" + STEP
                + "\",\"taskType\":\"SYNTHETIC_CORE\",\"capabilityCode\":\"CORE\"," 
                + "\"attemptNo\":1,\"packageHash\":\"" + packageHash
                + "\",\"input\":{\"fixtureId\":\"fixture:core-success-v1\"," 
                + "\"instruction\":\"synthetic\",\"expectedOutcome\":\"SUCCEED\"},"
                + "\"externalActionsEnabled\":false,\"deadlineAt\":\"2026-09-12T12:10:00Z\"}";
        var delivery = new AdenRunnerDeliveryRepository.ClaimedDelivery(
                workspaceId(), deliveryId(), TASK, STEP, 1, Long.MAX_VALUE,
                NOW.plusSeconds(60), taskPackage, packageHash, sessionId(), Long.MAX_VALUE);
        when(claims.claim(any())).thenReturn(new AdenRunnerClaimService.ClaimResult(
                CLAIM, SESSION, Long.MAX_VALUE,
                List.of(new AdenRunnerClaimService.ClaimItem(delivery)), NOW, false));

        var response = controller.claim("claim:transport-key",
                new AdenRunnerController.ClaimRequest(CLAIM, Set.of(AdenCapabilityCode.CORE), 1, 1),
                sessionAuthentication(Long.MAX_VALUE));

        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
        assertEquals(CLAIM, response.getBody().claimRequestId());
        assertEquals("9223372036854775807", response.getBody().sessionEpoch());
        assertEquals("9223372036854775807", response.getBody().items().get(0).fencingToken());
        assertEquals(packageHash,
                response.getBody().items().get(0).taskPackage().path("packageHash").asText());

        var captor = org.mockito.ArgumentCaptor.forClass(AdenRunnerClaimService.ClaimCommand.class);
        verify(claims).claim(captor.capture());
        assertEquals("claim:transport-key", captor.getValue().idempotencyKey().value());
        assertEquals(CLAIM, captor.getValue().claimRequestId());
    }

    @Test
    void splitHeartbeatResponsesKeepCanonicalStringValuesAndCancelSignal() {
        when(heartbeats.heartbeatSession(any())).thenReturn(new AdenRunnerHeartbeatService.HeartbeatResult(
                Long.MAX_VALUE, List.of(), SESSION, Long.MAX_VALUE,
                NOW.plusSeconds(900), NOW, List.of(deliveryId())));
        when(heartbeats.heartbeatDelivery(any())).thenReturn(
                new AdenRunnerHeartbeatService.DeliveryHeartbeatResult(
                        deliveryId(), "RUNNING", Long.MAX_VALUE, NOW.plusSeconds(60), true, NOW));

        var sessionResponse = controller.heartbeatSession(SESSION,
                new AdenRunnerController.SessionHeartbeatRequest(
                        Long.toString(Long.MAX_VALUE), "1", NOW, List.of(DELIVERY)),
                sessionAuthentication(Long.MAX_VALUE));
        var deliveryResponse = controller.heartbeatDelivery(DELIVERY,
                new AdenRunnerController.DeliveryHeartbeatRequest(
                        Long.toString(Long.MAX_VALUE), Long.toString(Long.MAX_VALUE), "1", NOW),
                sessionAuthentication(Long.MAX_VALUE));

        assertEquals(List.of(DELIVERY), sessionResponse.getBody().cancelDeliveryIds());
        assertEquals("9223372036854775807", sessionResponse.getBody().sessionEpoch());
        assertEquals("9223372036854775807", deliveryResponse.getBody().fencingToken());
        assertTrue(deliveryResponse.getBody().cancelRequested());
    }

    @Test
    void receiptResponseUsesCanonicalUnionAndStringWatermarks() {
        when(receipts.apply(any())).thenReturn(new AdenRunnerReceiptService.ReceiptResult(
                RECEIPT, DELIVERY, TASK, Long.MAX_VALUE, "SUCCEEDED", "SUCCEEDED", "SUCCEEDED",
                Long.MAX_VALUE, NOW, CORRELATION, false));
        Map<String, Object> data = Map.of(
                "resultHash", "4".repeat(64),
                "finishedAt", NOW.toString(),
                "summary", "合成执行完成，未产生外部副作用。");

        var response = controller.receipt(DELIVERY, "receipt:transport-key",
                new AdenRunnerController.ReceiptRequest(RECEIPT, Long.toString(Long.MAX_VALUE),
                        Long.toString(Long.MAX_VALUE), Long.toString(Long.MAX_VALUE),
                        AdenRunnerController.ReceiptKind.SUCCEEDED, NOW, data),
                sessionAuthentication(Long.MAX_VALUE), requestWithCorrelation());

        assertNotNull(response.getBody());
        assertEquals("ACCEPTED", response.getBody().disposition());
        assertEquals("9223372036854775807", response.getBody().taskVersion());
        assertEquals("9223372036854775807", response.getBody().lastReceiptSequence());
        assertEquals(CORRELATION, response.getBody().correlationId());
    }

    @Test
    void receiptUnionRejectsUnknownOrWrongTypedDataBeforeApplicationMutation() {
        Map<String, Object> withUnknown = new LinkedHashMap<>();
        withUnknown.put("startedAt", NOW.toString());
        withUnknown.put("unexpected", true);
        var request = new AdenRunnerController.ReceiptRequest(RECEIPT, "1", "1", "1",
                AdenRunnerController.ReceiptKind.STARTED, NOW, withUnknown);

        assertThrows(IllegalArgumentException.class, () -> controller.receipt(
                DELIVERY, "receipt:transport-key", request,
                sessionAuthentication(1), requestWithCorrelation()));
        verify(receipts, never()).apply(any());

        Map<String, Object> wrongProgress = Map.of("progressPercent", 10.5d);
        var progress = new AdenRunnerController.ReceiptRequest(RECEIPT, "1", "1", "1",
                AdenRunnerController.ReceiptKind.PROGRESS, NOW, wrongProgress);
        assertThrows(IllegalArgumentException.class, () -> controller.receipt(
                DELIVERY, "receipt:transport-key", progress,
                sessionAuthentication(1), requestWithCorrelation()));
    }

    @Test
    void beanValidationLocksSemverAndPositiveStringShapes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var badSession = new AdenRunnerController.SessionRequest(
                    1, "latest", 1, Set.of(AdenCapabilityCode.CORE));
            var badHeartbeat = new AdenRunnerController.DeliveryHeartbeatRequest(
                    "01", "1", "1", NOW);
            assertFalse(validator.validate(badSession).isEmpty());
            assertFalse(validator.validate(badHeartbeat).isEmpty());
        }
    }

    @Test
    void duplicateSessionHeartbeatDeliveryIdsAreRejectedBeforeRefresh() {
        var heartbeat = new AdenRunnerController.SessionHeartbeatRequest(
                "1", "1", NOW, List.of(DELIVERY, DELIVERY));
        assertThrows(IllegalArgumentException.class, () -> controller.heartbeatSession(
                SESSION, heartbeat, sessionAuthentication(1)));
        verify(heartbeats, never()).heartbeatSession(any());
    }

    private static MockHttpServletRequest requestWithCorrelation() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(com.ruoyi.aden.api.common.AdenCorrelationIdFilter.REQUEST_ATTRIBUTE, CORRELATION);
        return request;
    }

    private static UsernamePasswordAuthenticationToken credentialAuthentication() {
        var principal = new AdenRunnerCredentialPrincipal(
                workspaceId(), runnerId(), new AdenCredentialId(CREDENTIAL), 1);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    private static UsernamePasswordAuthenticationToken sessionAuthentication(long epoch) {
        var principal = new AdenRunnerSessionPrincipal(workspaceId(), runnerId(), sessionId(), epoch);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    private static AdenWorkspaceId workspaceId() { return new AdenWorkspaceId(WORKSPACE); }
    private static AdenRunnerId runnerId() { return new AdenRunnerId(RUNNER); }
    private static AdenSessionId sessionId() { return new AdenSessionId(SESSION); }
    private static AdenDeliveryId deliveryId() { return new AdenDeliveryId(DELIVERY); }
}
