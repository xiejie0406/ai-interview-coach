package com.aiinterviewcoach.adapters.outbound.persistence.shared;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

/** 用于加密前的确定性结构化文本；长度按 UTF-16 code unit 计，与 String.length 一致。 */
public final class LengthPrefixedCodec {

    private LengthPrefixedCodec() {
    }

    public static String encode(List<String> values) {
        StringBuilder result = new StringBuilder().append(values.size()).append('|');
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("length-prefixed value must not be null");
            }
            result.append(value.length()).append(':').append(value);
        }
        return result.toString();
    }

    public static List<String> decode(String encoded) {
        try {
            int separator = encoded.indexOf('|');
            if (separator <= 0) {
                throw new IllegalArgumentException();
            }
            int count = Integer.parseInt(encoded.substring(0, separator));
            if (count < 0) {
                throw new IllegalArgumentException();
            }
            int offset = separator + 1;
            List<String> values = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int colon = encoded.indexOf(':', offset);
                if (colon < offset) {
                    throw new IllegalArgumentException();
                }
                int length = Integer.parseInt(encoded.substring(offset, colon));
                int start = colon + 1;
                int end = Math.addExact(start, length);
                if (length < 0 || end > encoded.length()) {
                    throw new IllegalArgumentException();
                }
                values.add(encoded.substring(start, end));
                offset = end;
            }
            if (offset != encoded.length()) {
                throw new IllegalArgumentException();
            }
            return List.copyOf(values);
        } catch (RuntimeException exception) {
            throw new DataIntegrityViolationException("invalid encrypted structured payload", exception);
        }
    }
}
