package com.ruoyi.aden.application.projection;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/** HMAC 保护的不可伪造游标；payload 与签名一起置于单段 base64url。 */
public final class AdenOpaqueCursorCodec {
    private static final int SIGNATURE_BYTES = 32;
    private final byte[] key;

    public AdenOpaqueCursorCodec(byte[] rootSecret) {
        if (rootSecret == null || rootSecret.length < 32) throw new IllegalArgumentException("cursor secret 至少 32 字节");
        this.key = hmac(rootSecret, "aden-opaque-cursor-v1".getBytes(StandardCharsets.UTF_8));
    }

    public String encodePage(String purpose, AdenWorkspaceId workspaceId, String queryHash,
                             Instant positionAt, String positionId) {
        return encode("aden-p1.", String.join("\n", "P", purpose, workspaceId.value(), queryHash,
                positionAt.toString(), positionId));
    }

    public PageCursor decodePage(String token, String purpose, AdenWorkspaceId workspaceId,
                                 String queryHash) {
        String[] values = decode(token, "aden-p1.", 6);
        if (!"P".equals(values[0]) || !purpose.equals(values[1])
                || !workspaceId.value().equals(values[2]) || !queryHash.equals(values[3])) {
            throw invalid(false);
        }
        try {
            return new PageCursor(Instant.parse(values[4]), values[5]);
        } catch (RuntimeException exception) {
            throw invalid(false);
        }
    }

    public String encodeStream(AdenWorkspaceId workspaceId, String filterHash,
                               long sequence, Instant issuedAt) {
        if (sequence < 0) throw new IllegalArgumentException("stream sequence 不得为负数");
        return encode("aden-c1.", String.join("\n", "S", workspaceId.value(), filterHash,
                Long.toString(sequence), issuedAt.toString()));
    }

    public StreamCursor decodeStream(String token, AdenWorkspaceId workspaceId, String filterHash) {
        String[] values = decode(token, "aden-c1.", 5);
        if (!"S".equals(values[0]) || !workspaceId.value().equals(values[1])
                || !filterHash.equals(values[2])) throw invalid(true);
        try {
            long sequence = Long.parseLong(values[3]);
            if (sequence < 0 || !Long.toString(sequence).equals(values[3])) throw new NumberFormatException();
            return new StreamCursor(sequence, Instant.parse(values[4]));
        } catch (RuntimeException exception) {
            throw invalid(true);
        }
    }

    private String encode(String prefix, String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signed = Arrays.copyOf(bytes, bytes.length + SIGNATURE_BYTES);
        System.arraycopy(hmac(key, bytes), 0, signed, bytes.length, SIGNATURE_BYTES);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
    }

    private String[] decode(String token, String prefix, int fields) {
        try {
            if (token == null || token.length() < 16 || token.length() > 768 || !token.startsWith(prefix)) {
                throw invalid(prefix.startsWith("aden-c1"));
            }
            byte[] signed = Base64.getUrlDecoder().decode(token.substring(prefix.length()));
            if (signed.length <= SIGNATURE_BYTES) throw invalid(prefix.startsWith("aden-c1"));
            byte[] payload = Arrays.copyOf(signed, signed.length - SIGNATURE_BYTES);
            byte[] signature = Arrays.copyOfRange(signed, signed.length - SIGNATURE_BYTES, signed.length);
            if (!MessageDigest.isEqual(signature, hmac(key, payload))) throw invalid(prefix.startsWith("aden-c1"));
            String[] values = new String(payload, StandardCharsets.UTF_8).split("\n", -1);
            if (values.length != fields) throw invalid(prefix.startsWith("aden-c1"));
            return values;
        } catch (RuntimeException exception) {
            if (exception instanceof AdenApplicationException applicationException) throw applicationException;
            throw invalid(prefix.startsWith("aden-c1"));
        }
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 不可用", exception);
        }
    }

    private static AdenApplicationException invalid(boolean stream) {
        return new AdenApplicationException(stream ? "ADEN_STREAM_CURSOR_INVALID" : "ADEN_INVALID_ARGUMENT",
                stream ? "事件游标无效或不属于当前工作空间" : "分页游标无效");
    }

    public record PageCursor(Instant positionAt, String positionId) { }
    public record StreamCursor(long sequence, Instant issuedAt) { }
}
