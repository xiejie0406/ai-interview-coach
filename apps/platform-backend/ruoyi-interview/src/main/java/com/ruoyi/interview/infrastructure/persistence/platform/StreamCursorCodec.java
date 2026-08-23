package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.infrastructure.persistence.shared.LengthPrefixedCodec;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Last-Event-ID 使用的不透明 cursor；tenant/stream 绑定由权威数据库行校验，不能只信 token。 */
final class StreamCursorCodec {

    private static final String FORMAT = "durable-stream-cursor-v1";

    String encode(long sequence, ResourceId eventId) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("stream cursor sequence must be positive");
        }
        String value = LengthPrefixedCodec.encode(List.of(
                FORMAT, Long.toString(sequence), eventId.value()));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    Optional<Decoded> decode(String cursor) {
        if (cursor == null || cursor.isBlank() || cursor.length() > 512) {
            return Optional.empty();
        }
        try {
            List<String> values = LengthPrefixedCodec.decode(new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (values.size() != 3 || !FORMAT.equals(values.getFirst())) {
                return Optional.empty();
            }
            long sequence = Long.parseLong(values.get(1));
            if (sequence <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Decoded(sequence, ResourceId.of(values.get(2))));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    record Decoded(long sequence, ResourceId eventId) { }
}

