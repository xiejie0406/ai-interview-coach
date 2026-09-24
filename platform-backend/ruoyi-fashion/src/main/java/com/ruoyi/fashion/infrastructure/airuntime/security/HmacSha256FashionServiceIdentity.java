package com.ruoyi.fashion.infrastructure.airuntime.security;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * HMAC-SHA256 请求签名的当前可测实现。部署时仍必须使用私网 TLS；该签名不替代传输加密。
 */
public final class HmacSha256FashionServiceIdentity implements FashionServiceIdentity {

    private final FashionServiceIdentityPolicy policy;
    private final FashionServiceIdentityKeyRing keyRing;
    private final FashionServiceReplayStore replayStore;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;

    public HmacSha256FashionServiceIdentity(
            FashionServiceIdentityPolicy policy,
            FashionServiceIdentityKeyRing keyRing,
            FashionServiceReplayStore replayStore,
            Clock clock,
            Supplier<String> nonceSupplier) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
        this.replayStore = Objects.requireNonNull(replayStore, "replayStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "nonceSupplier");
    }

    @Override
    public FashionServiceAuthHeaders sign(FashionServiceRequest request) {
        Objects.requireNonNull(request, "request");
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String nonce = validatedNonce(nonceSupplier.get());
        String contentSha256 = FashionServiceSignatureV1.bodyDigest(request.body());
        String canonical = FashionServiceSignatureV1.canonical(
                request.method(),
                request.rawPath(),
                policy.localServiceId(),
                policy.peerServiceId(),
                timestamp,
                nonce,
                contentSha256);
        String signature = FashionServiceSignatureV1.sign(keyRing.activeKey(), canonical);
        return new FashionServiceAuthHeaders(
                policy.localServiceId(),
                keyRing.activeKeyId(),
                timestamp,
                nonce,
                policy.peerServiceId(),
                contentSha256,
                signature);
    }

    @Override
    public FashionServicePrincipal verify(
            FashionServiceRequest request,
            FashionServiceAuthHeaders headers) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(headers, "headers");
        validateHeaderTokens(headers);
        Instant now = clock.instant();
        Instant signedAt = parseTimestamp(headers.timestamp());
        if (signedAt.isBefore(now.minus(policy.maxClockSkew()))
                || signedAt.isAfter(now.plus(policy.maxClockSkew()))) {
            throw failure(FashionServiceAuthFailure.EXPIRED);
        }
        String actualDigest = FashionServiceSignatureV1.bodyDigest(request.body());
        if (!FashionServiceSignatureV1.constantTimeEquals(actualDigest, headers.contentSha256())) {
            throw failure(FashionServiceAuthFailure.BODY_DIGEST_MISMATCH);
        }
        SecretKey verificationKey = keyRing.verificationKey(headers.keyId())
                .orElseThrow(() -> failure(FashionServiceAuthFailure.UNKNOWN_KEY));
        String canonical = FashionServiceSignatureV1.canonical(
                request.method(),
                request.rawPath(),
                headers.serviceId(),
                headers.audience(),
                headers.timestamp(),
                headers.nonce(),
                headers.contentSha256());
        String expectedSignature = FashionServiceSignatureV1.sign(verificationKey, canonical);
        if (!FashionServiceSignatureV1.constantTimeEquals(expectedSignature, headers.signature())) {
            throw failure(FashionServiceAuthFailure.BAD_SIGNATURE);
        }
        if (!policy.peerServiceId().equals(headers.serviceId())
                || !policy.localServiceId().equals(headers.audience())) {
            throw failure(FashionServiceAuthFailure.UNAUTHORIZED_SERVICE);
        }
        Instant nonceExpiry = now.plus(policy.nonceTtl());
        FashionServiceReplayStore.ReserveResult reserveResult = replayStore.reserve(
                headers.serviceId(), headers.nonce(), nonceExpiry, now);
        if (reserveResult == FashionServiceReplayStore.ReserveResult.REPLAYED) {
            throw failure(FashionServiceAuthFailure.REPLAYED_NONCE);
        }
        if (reserveResult == FashionServiceReplayStore.ReserveResult.CAPACITY_EXCEEDED) {
            throw failure(FashionServiceAuthFailure.CAPACITY_EXCEEDED);
        }
        return new FashionServicePrincipal(
                headers.serviceId(), headers.keyId(), headers.audience(), signedAt, headers.nonce());
    }

    @Override
    public boolean configured() {
        return true;
    }

    private static void validateHeaderTokens(FashionServiceAuthHeaders headers) {
        FashionServiceIdentityPolicy.serviceToken(headers.serviceId(), "serviceId");
        FashionServiceIdentityPolicy.serviceToken(headers.audience(), "audience");
        if (!headers.keyId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw failure(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
        validatedNonce(headers.nonce());
        if (!headers.timestamp().matches("[0-9]{10,12}")) {
            throw failure(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
        if (!headers.contentSha256().matches("[0-9a-f]{64}")) {
            throw failure(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
        if (!headers.signature().matches("v1=[A-Za-z0-9_-]{43}")) {
            throw failure(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
    }

    private static String validatedNonce(String nonce) {
        if (nonce == null || !nonce.matches("[A-Za-z0-9][A-Za-z0-9._:-]{15,127}")) {
            throw failure(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
        return nonce;
    }

    private static Instant parseTimestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException | DateTimeException exception) {
            throw failure(FashionServiceAuthFailure.MALFORMED_HEADER);
        }
    }

    private static FashionServiceAuthenticationException failure(FashionServiceAuthFailure failure) {
        return new FashionServiceAuthenticationException(failure);
    }
}
