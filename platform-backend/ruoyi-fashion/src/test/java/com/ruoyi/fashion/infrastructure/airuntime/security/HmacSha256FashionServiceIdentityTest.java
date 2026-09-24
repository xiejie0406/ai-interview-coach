package com.ruoyi.fashion.infrastructure.airuntime.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSha256FashionServiceIdentityTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_799_721_000L);
    private static final String NONCE = "00000000-0000-4000-8000-000000000001";
    private static final byte[] CURRENT_KEY = "0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] PREVIOUS_KEY = "abcdef0123456789abcdef0123456789"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void matchesCrossLanguageSignatureVector() {
        byte[] body = "{\"runId\":\"run-001\"}".getBytes(StandardCharsets.UTF_8);
        String digest = FashionServiceSignatureV1.bodyDigest(body);
        String canonical = FashionServiceSignatureV1.canonical(
                "POST",
                "/internal/v1/agent-runs:execute",
                "ruoyi-fashion",
                "fashion-ai-runtime",
                "1799721000",
                NONCE,
                digest);

        assertThat(digest)
                .isEqualTo("f08729f55ce4df7e5125c728c0112224acfbcc6ecaac181ddb3fabeed3a8bd3b");
        assertThat(FashionServiceSignatureV1.sign(
                new SecretKeySpec(CURRENT_KEY, "HmacSHA256"), canonical))
                .isEqualTo("v1=AA_3Re8jy5kY7izB-J7GQH6Y69STfjVbvrGy4nkc9Ho");
    }

    @Test
    void consumesEverySharedCrossLanguageSignatureVector() throws Exception {
        Path vectorPath = workspaceRoot().resolve(
                "contracts/fashion/examples/v1/service-authentication-vectors.json");
        JsonNode vectors = new ObjectMapper().readTree(vectorPath.toFile()).required("vectors");

        for (JsonNode vector : vectors) {
            byte[] body = vector.required("body_utf8").asText().getBytes(StandardCharsets.UTF_8);
            byte[] key = Base64.getDecoder().decode(vector.required("secret_base64").asText());
            String digest = FashionServiceSignatureV1.bodyDigest(body);
            String canonical = FashionServiceSignatureV1.canonical(
                    vector.required("method").asText(),
                    vector.required("raw_path").asText(),
                    vector.required("service_id").asText(),
                    vector.required("audience").asText(),
                    vector.required("timestamp").asText(),
                    vector.required("nonce").asText(),
                    digest);

            assertThat(digest).isEqualTo(vector.required("content_sha256").asText());
            assertThat(canonical).isEqualTo(vector.required("canonical_utf8").asText());
            assertThat(FashionServiceSignatureV1.sign(
                    new SecretKeySpec(key, "HmacSHA256"), canonical))
                    .isEqualTo(vector.required("signature").asText());
        }
    }

    @Test
    void javaAndRuntimePoliciesAuthenticateTheSameRequest() {
        FashionServiceIdentity javaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        FashionServiceIdentity runtimeIdentity = identity(
                "fashion-ai-runtime", "ruoyi-fashion", "current", CURRENT_KEY, NOW, "runtime-nonce-0001");
        FashionServiceRequest request = request("{\"runId\":\"run-001\"}");

        FashionServiceAuthHeaders headers = javaIdentity.sign(request);
        FashionServicePrincipal principal = runtimeIdentity.verify(request, headers);

        assertThat(principal.serviceId()).isEqualTo("ruoyi-fashion");
        assertThat(principal.audience()).isEqualTo("fashion-ai-runtime");
        assertThat(principal.keyId()).isEqualTo("current");
        assertThat(headers.signature()).startsWith("v1=");
        assertThat(headers.toMap()).doesNotContainKey("Authorization");
    }

    @Test
    void rejectsReplayAfterAValidSignature() {
        FashionServiceIdentity javaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        FashionServiceIdentity runtimeIdentity = identity(
                "fashion-ai-runtime", "ruoyi-fashion", "current", CURRENT_KEY, NOW, "runtime-nonce-0001");
        FashionServiceRequest request = request("{}");
        FashionServiceAuthHeaders headers = javaIdentity.sign(request);

        runtimeIdentity.verify(request, headers);

        assertAuthFailure(
                () -> runtimeIdentity.verify(request, headers),
                FashionServiceAuthFailure.REPLAYED_NONCE);
    }

    @Test
    void rejectsTamperedBodyBeforeReservingNonce() {
        FashionServiceIdentity javaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        FashionServiceIdentity runtimeIdentity = identity(
                "fashion-ai-runtime", "ruoyi-fashion", "current", CURRENT_KEY, NOW, "runtime-nonce-0001");
        FashionServiceAuthHeaders headers = javaIdentity.sign(request("{\"value\":1}"));

        assertAuthFailure(
                () -> runtimeIdentity.verify(request("{\"value\":2}"), headers),
                FashionServiceAuthFailure.BODY_DIGEST_MISMATCH);

        assertThat(runtimeIdentity.verify(request("{\"value\":1}"), headers).nonce()).isEqualTo(NONCE);
    }

    @Test
    void rejectsExpiredAndFutureSignatures() {
        FashionServiceIdentity javaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        FashionServiceAuthHeaders headers = javaIdentity.sign(request("{}"));
        FashionServiceIdentity lateRuntime = identity(
                "fashion-ai-runtime",
                "ruoyi-fashion",
                "current",
                CURRENT_KEY,
                NOW.plusSeconds(301),
                "runtime-nonce-0001");

        assertAuthFailure(
                () -> lateRuntime.verify(request("{}"), headers),
                FashionServiceAuthFailure.EXPIRED);
    }

    @Test
    void acceptsPreviousKeyDuringRotationAndSignsOnlyWithActiveKey() {
        FashionServiceIdentity oldJava = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "previous", PREVIOUS_KEY, NOW, NONCE);
        Map<String, byte[]> rotatingKeys = new LinkedHashMap<>();
        rotatingKeys.put("current", CURRENT_KEY);
        rotatingKeys.put("previous", PREVIOUS_KEY);
        FashionServiceIdentity runtime = new HmacSha256FashionServiceIdentity(
                policy("fashion-ai-runtime", "ruoyi-fashion"),
                FashionServiceIdentityKeyRing.of("current", rotatingKeys),
                new InMemoryFashionServiceReplayStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "runtime-nonce-0001");

        FashionServiceAuthHeaders oldHeaders = oldJava.sign(request("{}"));

        assertThat(runtime.verify(request("{}"), oldHeaders).keyId()).isEqualTo("previous");
        assertThat(runtime.sign(request("{}")).keyId()).isEqualTo("current");
    }

    @Test
    void nonceCannotBeReplayedAcrossKeysDuringRotation() {
        FashionServiceIdentity previousJava = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "previous", PREVIOUS_KEY, NOW, NONCE);
        FashionServiceIdentity currentJava = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        Map<String, byte[]> rotatingKeys = new LinkedHashMap<>();
        rotatingKeys.put("current", CURRENT_KEY);
        rotatingKeys.put("previous", PREVIOUS_KEY);
        FashionServiceIdentity runtime = new HmacSha256FashionServiceIdentity(
                policy("fashion-ai-runtime", "ruoyi-fashion"),
                FashionServiceIdentityKeyRing.of("current", rotatingKeys),
                new InMemoryFashionServiceReplayStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "runtime-nonce-0001");

        runtime.verify(request("{}"), previousJava.sign(request("{}")));

        assertAuthFailure(
                () -> runtime.verify(request("{}"), currentJava.sign(request("{}"))),
                FashionServiceAuthFailure.REPLAYED_NONCE);
    }

    @Test
    void rejectsWrongAudienceUnknownKeyAndMalformedSignature() {
        FashionServiceIdentity javaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        FashionServiceIdentity runtimeIdentity = identity(
                "fashion-ai-runtime", "ruoyi-fashion", "current", CURRENT_KEY, NOW, "runtime-nonce-0001");
        FashionServiceAuthHeaders valid = javaIdentity.sign(request("{}"));

        FashionServiceIdentity wrongAudienceIdentity = identity(
                "ruoyi-fashion", "another-service", "current", CURRENT_KEY, NOW, "wrong-audience-0001");
        FashionServiceAuthHeaders wrongAudience = wrongAudienceIdentity.sign(request("{}"));
        FashionServiceAuthHeaders unknownKey = copy(valid, "unknown", valid.audience(), valid.signature());
        FashionServiceAuthHeaders badSignature = copy(
                valid,
                valid.keyId(),
                valid.audience(),
                "v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        assertAuthFailure(
                () -> runtimeIdentity.verify(request("{}"), wrongAudience),
                FashionServiceAuthFailure.UNAUTHORIZED_SERVICE);
        assertAuthFailure(
                () -> runtimeIdentity.verify(request("{}"), unknownKey),
                FashionServiceAuthFailure.UNKNOWN_KEY);
        assertAuthFailure(
                () -> runtimeIdentity.verify(request("{}"), badSignature),
                FashionServiceAuthFailure.BAD_SIGNATURE);
    }

    @Test
    void unavailableIdentityFailsClosedAndMissingHeadersUseStable401Failure() {
        FashionServiceIdentity unavailable = new UnavailableFashionServiceIdentity();

        assertThat(unavailable.configured()).isFalse();
        assertAuthFailure(
                () -> unavailable.sign(request("{}")),
                FashionServiceAuthFailure.NOT_CONFIGURED);
        assertThatThrownBy(() -> FashionServiceAuthHeaders.from(Map.of()))
                .isInstanceOfSatisfying(FashionServiceAuthenticationException.class, exception -> {
                    assertThat(exception.failure()).isEqualTo(FashionServiceAuthFailure.MISSING_HEADER);
                    assertThat(exception.httpStatus()).isEqualTo(401);
                });
    }

    @Test
    void rejectsCaseVariantDuplicateAuthenticationHeaders() {
        Map<String, String> duplicateHeaders = new LinkedHashMap<>();
        duplicateHeaders.put(FashionServiceAuthHeaderNames.SERVICE_ID, "ruoyi-fashion");
        duplicateHeaders.put(FashionServiceAuthHeaderNames.SERVICE_ID.toLowerCase(), "ruoyi-fashion");

        assertAuthFailure(
                () -> FashionServiceAuthHeaders.from(duplicateHeaders),
                FashionServiceAuthFailure.MALFORMED_HEADER);
    }

    @Test
    void requestBodyIsDefensivelyCopiedAndQueryIsRejected() {
        byte[] original = "{}".getBytes(StandardCharsets.UTF_8);
        FashionServiceRequest request = new FashionServiceRequest("post", "/internal/v1/test", original);
        original[0] = '!';
        byte[] returned = request.body();
        returned[0] = '!';

        assertThat(new String(request.body(), StandardCharsets.UTF_8)).isEqualTo("{}");
        assertThat(request.method()).isEqualTo("POST");
        assertThatThrownBy(() -> new FashionServiceRequest("GET", "/internal/v1/test?q=1", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replayStoreFailsClosedAtCapacityAndReclaimsExpiredEntries() {
        InMemoryFashionServiceReplayStore store = new InMemoryFashionServiceReplayStore(1);

        assertThat(store.reserve("service", "nonce-0000000001", NOW.plusSeconds(600), NOW))
                .isEqualTo(FashionServiceReplayStore.ReserveResult.CLAIMED);
        assertThat(store.reserve("service", "nonce-0000000001", NOW.plusSeconds(600), NOW))
                .isEqualTo(FashionServiceReplayStore.ReserveResult.REPLAYED);
        assertThat(store.reserve("service", "nonce-0000000002", NOW.plusSeconds(600), NOW))
                .isEqualTo(FashionServiceReplayStore.ReserveResult.CAPACITY_EXCEEDED);
        assertThat(store.reserve(
                "service", "nonce-0000000002", NOW.plusSeconds(1_201), NOW.plusSeconds(601)))
                .isEqualTo(FashionServiceReplayStore.ReserveResult.CLAIMED);
    }

    @Test
    void mapsReplayCapacityProtectionTo429WithoutMisreportingAReplay() {
        FashionServiceIdentity javaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW, NONCE);
        FashionServiceIdentity runtimeIdentity = new HmacSha256FashionServiceIdentity(
                policy("fashion-ai-runtime", "ruoyi-fashion"),
                FashionServiceIdentityKeyRing.of("current", Map.of("current", CURRENT_KEY)),
                new InMemoryFashionServiceReplayStore(1),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "runtime-nonce-0001");

        runtimeIdentity.verify(request("{}"), javaIdentity.sign(request("{}")));
        FashionServiceIdentity secondJavaIdentity = identity(
                "ruoyi-fashion", "fashion-ai-runtime", "current", CURRENT_KEY, NOW,
                "00000000-0000-4000-8000-000000000002");

        assertThatThrownBy(() -> runtimeIdentity.verify(
                request("{}"), secondJavaIdentity.sign(request("{}"))))
                .isInstanceOfSatisfying(FashionServiceAuthenticationException.class, exception -> {
                    assertThat(exception.failure()).isEqualTo(FashionServiceAuthFailure.CAPACITY_EXCEEDED);
                    assertThat(exception.httpStatus()).isEqualTo(429);
                });
    }

    private static FashionServiceAuthHeaders copy(
            FashionServiceAuthHeaders source,
            String keyId,
            String audience,
            String signature) {
        return new FashionServiceAuthHeaders(
                source.serviceId(),
                keyId,
                source.timestamp(),
                source.nonce(),
                audience,
                source.contentSha256(),
                signature);
    }

    private static FashionServiceIdentity identity(
            String local,
            String peer,
            String activeKeyId,
            byte[] key,
            Instant now,
            String nonce) {
        return new HmacSha256FashionServiceIdentity(
                policy(local, peer),
                FashionServiceIdentityKeyRing.of(activeKeyId, Map.of(activeKeyId, key)),
                new InMemoryFashionServiceReplayStore(),
                Clock.fixed(now, ZoneOffset.UTC),
                () -> nonce);
    }

    private static FashionServiceIdentityPolicy policy(String local, String peer) {
        return new FashionServiceIdentityPolicy(
                local, peer, Duration.ofMinutes(5), Duration.ofMinutes(10));
    }

    private static FashionServiceRequest request(String body) {
        return new FashionServiceRequest(
                "POST",
                "/internal/v1/agent-runs:execute",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static Path workspaceRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("contracts/fashion/ai-runtime.openapi.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("找不到 contracts/fashion 契约目录");
    }

    private static void assertAuthFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            FashionServiceAuthFailure expectedFailure) {
        int expectedStatus = switch (expectedFailure) {
            case UNAUTHORIZED_SERVICE -> 403;
            case CAPACITY_EXCEEDED -> 429;
            default -> 401;
        };
        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(FashionServiceAuthenticationException.class, exception -> {
                    assertThat(exception.failure()).isEqualTo(expectedFailure);
                    assertThat(exception.httpStatus()).isEqualTo(expectedStatus);
                });
    }
}
