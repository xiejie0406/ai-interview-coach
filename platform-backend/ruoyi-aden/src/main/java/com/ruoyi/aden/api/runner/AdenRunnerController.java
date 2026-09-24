package com.ruoyi.aden.api.runner;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.api.common.AdenCorrelationIdFilter;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.runner.AdenRunnerClaimService;
import com.ruoyi.aden.application.runner.AdenRunnerCredentialPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerHeartbeatService;
import com.ruoyi.aden.application.runner.AdenRunnerReceiptService;
import com.ruoyi.aden.application.runner.AdenRunnerSessionPrincipal;
import com.ruoyi.aden.application.runner.AdenRunnerSessionService;
import com.ruoyi.aden.configuration.AdenProperties;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenReceiptType;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 严格实现 contracts/aden/openapi/runner-v1.openapi.json 的 Runner v1 适配器。 */
@Validated
@RestController
@RequestMapping("/api/v1/aden/runner/v1")
@ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true")
public class AdenRunnerController {
    private static final String POSITIVE_INT64 = "^[1-9][0-9]{0,18}$";

    private final AdenRunnerSessionService sessions;
    private final AdenRunnerClaimService claims;
    private final AdenRunnerHeartbeatService heartbeats;
    private final AdenRunnerReceiptService receipts;
    private final ObjectMapper objectMapper;
    private final int heartbeatAfterSeconds;

    public AdenRunnerController(AdenRunnerSessionService sessions,
                                AdenRunnerClaimService claims,
                                AdenRunnerHeartbeatService heartbeats,
                                AdenRunnerReceiptService receipts,
                                ObjectMapper objectMapper,
                                AdenProperties properties) {
        this.sessions = sessions;
        this.claims = claims;
        this.heartbeats = heartbeats;
        this.receipts = receipts;
        this.objectMapper = objectMapper;
        this.heartbeatAfterSeconds = properties.getRunner().getSession().getHeartbeatIntervalSeconds();
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> exchange(@Valid @RequestBody SessionRequest request,
                                                    Authentication authentication,
                                                    HttpServletRequest servletRequest) {
        AdenRunnerCredentialPrincipal principal = require(authentication, AdenRunnerCredentialPrincipal.class);
        var result = sessions.exchange(principal, new AdenRunnerSessionService.CreateSession(
                request.protocolVersion(), request.runnerVersion(), request.capacity(), request.capabilities()),
                AdenCorrelationIdFilter.correlationId(servletRequest));
        var body = new SessionResponse(result.runnerId().value(), result.workspaceId().value(),
                result.sessionId().value(), result.sessionToken(), Long.toString(result.sessionEpoch()),
                "ACTIVE", result.expiresAt(), heartbeatAfterSeconds, result.issuedAt());
        return noStore(HttpStatus.CREATED, body);
    }

    @PostMapping("/deliveries:claim")
    public ResponseEntity<ClaimResponse> claim(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ClaimRequest request,
            Authentication authentication) {
        AdenRunnerSessionPrincipal principal = require(authentication, AdenRunnerSessionPrincipal.class);
        String claimRequestId = canonicalUuid(request.claimRequestId(), "claimRequestId");
        var result = claims.claim(new AdenRunnerClaimService.ClaimCommand(principal,
                new AdenIdempotencyKey(idempotencyKey), claimRequestId,
                request.capabilities(), request.capacity(), request.batchLimit()));
        var body = new ClaimResponse(result.claimRequestId(), result.sessionId(),
                Long.toString(result.sessionEpoch()), result.items().stream().map(item -> {
                    var delivery = item.delivery();
                    return new ClaimedDeliveryResponse(delivery.deliveryId().value(), "LEASED",
                            Long.toString(delivery.fenceToken()), delivery.leaseUntil(),
                            taskPackage(delivery.taskPackageJson(), delivery.taskPackageHash()));
                }).toList(), result.serverTime());
        return noStore(HttpStatus.OK, body);
    }

    @PostMapping("/sessions/{sessionId}/heartbeats")
    public ResponseEntity<SessionHeartbeatResponse> heartbeatSession(
            @PathVariable String sessionId,
            @Valid @RequestBody SessionHeartbeatRequest request,
            Authentication authentication) {
        AdenRunnerSessionPrincipal principal = require(authentication, AdenRunnerSessionPrincipal.class);
        requirePathId(sessionId, principal.sessionId().value(), "sessionId");
        requireUnique(request.activeDeliveryIds(), "activeDeliveryIds");
        var result = heartbeats.heartbeatSession(new AdenRunnerHeartbeatService.SessionHeartbeatCommand(
                principal, positiveLong(request.sessionEpoch(), "sessionEpoch"),
                positiveLong(request.heartbeatSequence(), "heartbeatSequence"), request.observedAt(),
                request.activeDeliveryIds().stream()
                        .map(value -> new AdenDeliveryId(canonicalUuid(value, "activeDeliveryId"))).toList()));
        var body = new SessionHeartbeatResponse(result.sessionId(), Long.toString(result.sessionEpoch()),
                "ACTIVE", result.sessionExpiresAt(), result.serverTime(), result.cancelDeliveryIds().stream()
                .map(AdenDeliveryId::value).toList());
        return noStore(HttpStatus.OK, body);
    }

    @PostMapping("/deliveries/{deliveryId}/heartbeats")
    public ResponseEntity<DeliveryHeartbeatResponse> heartbeatDelivery(
            @PathVariable String deliveryId,
            @Valid @RequestBody DeliveryHeartbeatRequest request,
            Authentication authentication) {
        AdenRunnerSessionPrincipal principal = require(authentication, AdenRunnerSessionPrincipal.class);
        AdenDeliveryId id = new AdenDeliveryId(canonicalUuid(deliveryId, "deliveryId"));
        var result = heartbeats.heartbeatDelivery(new AdenRunnerHeartbeatService.DeliveryHeartbeatCommand(
                principal, id, positiveLong(request.sessionEpoch(), "sessionEpoch"),
                positiveLong(request.fencingToken(), "fencingToken"),
                positiveLong(request.heartbeatSequence(), "heartbeatSequence"), request.observedAt()));
        var body = new DeliveryHeartbeatResponse(result.deliveryId().value(), result.state(),
                Long.toString(result.fenceToken()), result.leaseUntil(),
                result.cancelRequested(), result.serverTime());
        return noStore(HttpStatus.OK, body);
    }

    @PostMapping("/deliveries/{deliveryId}/receipts")
    public ResponseEntity<ReceiptResponse> receipt(
            @PathVariable String deliveryId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceiptRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AdenRunnerSessionPrincipal principal = require(authentication, AdenRunnerSessionPrincipal.class);
        AdenDeliveryId id = new AdenDeliveryId(canonicalUuid(deliveryId, "deliveryId"));
        String receiptId = canonicalUuid(request.receiptId(), "receiptId");
        long sessionEpoch = positiveLong(request.sessionEpoch(), "sessionEpoch");
        if (sessionEpoch != principal.sessionEpoch()) {
            throw new IllegalArgumentException("sessionEpoch 与认证上下文不一致");
        }
        validateReceiptData(request.kind(), request.data());
        String correlationId = AdenCorrelationIdFilter.correlationId(servletRequest);
        var result = receipts.apply(new AdenRunnerReceiptService.ReceiptCommand(
                principal, new AdenIdempotencyKey(idempotencyKey), id, receiptId,
                positiveLong(request.fencingToken(), "fencingToken"),
                positiveLong(request.receiptSequence(), "receiptSequence"),
                internalType(request.kind()), request.observedAt(), request.data(), correlationId));
        var body = new ReceiptResponse(result.receiptId(), result.replayed() ? "REPLAYED" : "ACCEPTED",
                result.deliveryId(), result.deliveryState(), result.taskId(), result.taskState(),
                Long.toString(result.taskVersion()), Long.toString(result.receiptSequence()),
                result.acceptedAt(), result.correlationId());
        return noStore(HttpStatus.OK, body);
    }

    private JsonNode taskPackage(String rawJson, String expectedHash) {
        try {
            JsonNode value = objectMapper.readTree(rawJson);
            if (!value.isObject() || !expectedHash.equals(value.path("packageHash").asText())) {
                throw new IllegalStateException("Delivery TaskPackage 与持久化 hash 不一致");
            }
            return value;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Delivery TaskPackage 不是合法 JSON", exception);
        }
    }

    private static AdenReceiptType internalType(ReceiptKind kind) {
        return switch (kind) {
            case STARTED -> AdenReceiptType.STARTED;
            case PROGRESS -> AdenReceiptType.PROGRESS;
            case SUCCEEDED -> AdenReceiptType.COMPLETED;
            case FAILED_RETRYABLE -> AdenReceiptType.FAILED_RETRYABLE;
            case FAILED_FINAL -> AdenReceiptType.FAILED_FINAL;
            case CANCELED_AT_SAFE_POINT -> AdenReceiptType.CANCELED_SAFE_POINT;
            case OUTCOME_UNKNOWN -> AdenReceiptType.OUTCOME_UNKNOWN;
        };
    }

    /**
     * Bean Validation 只能约束回执公共外壳；这里按 kind 收紧 data 的判别联合，
     * 保证 Java 入口与 runner.schema.json 的 additionalProperties=false 语义一致。
     */
    private static void validateReceiptData(ReceiptKind kind, Map<String, Object> data) {
        switch (kind) {
            case STARTED -> {
                requireKeys(data, Set.of("startedAt"), Set.of("startedAt"));
                requireInstant(data, "startedAt");
            }
            case PROGRESS -> {
                requireKeys(data, Set.of("progressPercent"), Set.of("progressPercent", "message"));
                Object progress = data.get("progressPercent");
                if (!(progress instanceof Integer value) || value < 0 || value > 100) {
                    throw new IllegalArgumentException("data.progressPercent 必须是 0..100 的整数");
                }
                if (data.containsKey("message")) requireString(data, "message", 0, 240, null);
            }
            case SUCCEEDED -> {
                requireKeys(data, Set.of("resultHash", "finishedAt", "summary"),
                        Set.of("resultHash", "finishedAt", "summary"));
                requireString(data, "resultHash", 64, 64, "^[a-f0-9]{64}$");
                requireInstant(data, "finishedAt");
                requireString(data, "summary", 1, 500, null);
            }
            case FAILED_RETRYABLE, FAILED_FINAL -> {
                requireKeys(data, Set.of("reasonCode", "finishedAt"), Set.of("reasonCode", "finishedAt"));
                requireString(data, "reasonCode", 3, 64, "^[A-Z][A-Z0-9_]{2,63}$");
                requireInstant(data, "finishedAt");
            }
            case CANCELED_AT_SAFE_POINT -> {
                requireKeys(data, Set.of("safePoint", "finishedAt"), Set.of("safePoint", "finishedAt"));
                requireString(data, "safePoint", 1, 120, null);
                requireInstant(data, "finishedAt");
            }
            case OUTCOME_UNKNOWN -> {
                requireKeys(data, Set.of("reasonCode", "observedAt"), Set.of("reasonCode", "observedAt"));
                requireString(data, "reasonCode", 3, 64, "^[A-Z][A-Z0-9_]{2,63}$");
                requireInstant(data, "observedAt");
            }
        }
    }

    private static void requireKeys(Map<String, Object> data, Set<String> required, Set<String> allowed) {
        if (!data.keySet().containsAll(required) || !allowed.containsAll(data.keySet())) {
            throw new IllegalArgumentException("data 字段与回执 kind 不匹配");
        }
    }

    private static void requireInstant(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("data." + field + " 必须是 RFC 3339 时间字符串");
        }
        try {
            Instant.parse(text);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("data." + field + " 必须是 RFC 3339 UTC 时间字符串");
        }
    }

    private static void requireString(Map<String, Object> data, String field,
                                      int minimum, int maximum, String pattern) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.length() < minimum || text.length() > maximum
                || (pattern != null && !text.matches(pattern))) {
            throw new IllegalArgumentException("data." + field + " 不符合 Runner v1 契约");
        }
    }

    private static void requireUnique(List<String> values, String field) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + " 不允许重复值");
        }
    }

    private static long positiveLong(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " 必须是 canonical positive int64");
        }
    }

    private static String canonicalUuid(String value, String field) {
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value)) throw new IllegalArgumentException();
            return canonical;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " 必须是 canonical UUID");
        }
    }

    private static void requirePathId(String actual, String expected, String field) {
        if (!canonicalUuid(actual, field).equals(expected)) {
            throw new IllegalArgumentException(field + " 与认证上下文不一致");
        }
    }

    private static <T> T require(Authentication authentication, Class<T> type) {
        if (authentication == null || !type.isInstance(authentication.getPrincipal())) {
            throw new com.ruoyi.aden.application.error.AdenApplicationException(
                    "ADEN_AUTH_REQUIRED", "Runner 认证上下文无效");
        }
        return type.cast(authentication.getPrincipal());
    }

    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    public record SessionRequest(@Min(1) @Max(1) int protocolVersion,
                                 @NotBlank @Size(max = 64)
                                 @Pattern(regexp = "^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?$")
                                 String runnerVersion,
                                 @Min(1) @Max(32) int capacity,
                                 @NotEmpty @Size(max = 4)
                                 Set<@NotNull AdenCapabilityCode> capabilities) { }

    public record SessionResponse(String runnerId, String workspaceId, String sessionId,
                                  String sessionToken, String sessionEpoch, String state,
                                  Instant expiresAt, int heartbeatAfterSeconds,
                                  Instant issuedAt) { }

    public record ClaimRequest(@NotBlank String claimRequestId,
                               @NotEmpty @Size(max = 4)
                               Set<@NotNull AdenCapabilityCode> capabilities,
                               @Min(1) @Max(32) int capacity,
                               @Min(1) @Max(16) int batchLimit) { }

    public record ClaimResponse(String claimRequestId, String sessionId, String sessionEpoch,
                                List<ClaimedDeliveryResponse> items, Instant serverTime) { }

    public record ClaimedDeliveryResponse(String deliveryId, String state, String fencingToken,
                                          Instant leaseUntil, JsonNode taskPackage) { }

    public record SessionHeartbeatRequest(@NotBlank @Pattern(regexp = POSITIVE_INT64) String sessionEpoch,
                                          @NotBlank @Pattern(regexp = POSITIVE_INT64) String heartbeatSequence,
                                          @NotNull Instant observedAt,
                                          @NotNull @Size(max = 32) List<@NotBlank String> activeDeliveryIds) { }

    public record SessionHeartbeatResponse(String sessionId, String sessionEpoch, String state,
                                           Instant expiresAt, Instant serverTime,
                                           List<String> cancelDeliveryIds) { }

    public record DeliveryHeartbeatRequest(@NotBlank @Pattern(regexp = POSITIVE_INT64) String sessionEpoch,
                                           @NotBlank @Pattern(regexp = POSITIVE_INT64) String fencingToken,
                                           @NotBlank @Pattern(regexp = POSITIVE_INT64) String heartbeatSequence,
                                           @NotNull Instant observedAt) { }

    public record DeliveryHeartbeatResponse(String deliveryId, String state, String fencingToken,
                                            Instant leaseUntil, boolean cancelRequested,
                                            Instant serverTime) { }

    public enum ReceiptKind {
        STARTED, PROGRESS, SUCCEEDED, FAILED_RETRYABLE, FAILED_FINAL,
        CANCELED_AT_SAFE_POINT, OUTCOME_UNKNOWN
    }

    public record ReceiptRequest(@NotBlank String receiptId,
                                 @NotBlank @Pattern(regexp = POSITIVE_INT64) String sessionEpoch,
                                 @NotBlank @Pattern(regexp = POSITIVE_INT64) String fencingToken,
                                 @NotBlank @Pattern(regexp = POSITIVE_INT64) String receiptSequence,
                                 @NotNull ReceiptKind kind,
                                 @NotNull Instant observedAt,
                                 @NotNull @Size(max = 64) Map<String, Object> data) { }

    public record ReceiptResponse(String receiptId, String disposition, String deliveryId,
                                  String deliveryState, String taskId, String taskState,
                                  String taskVersion, String lastReceiptSequence,
                                  Instant acceptedAt, String correlationId) { }
}
