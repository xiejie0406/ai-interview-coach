package com.aiinterviewcoach.adapters.outbound.persistence.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 只处理 JSON string array 和 string-to-string object，避免把 persistence 契约绑定到
 * 某个 Jackson major version。解析器拒绝数字、布尔、null 和嵌套对象，防止静默丢字段。
 */
@Component
public final class PersistenceJsonCodec {

    public String write(Map<String, String> values) {
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (!first) {
                result.append(',');
            }
            result.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
            first = false;
        }
        return result.append('}').toString();
    }

    public String write(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(quote(values.get(index)));
        }
        return result.append(']').toString();
    }

    public Map<String, String> readStringMap(String json) {
        Parser parser = new Parser(json);
        Map<String, String> result = new LinkedHashMap<>();
        parser.expect('{');
        if (!parser.peek('}')) {
            do {
                String key = parser.string();
                parser.expect(':');
                String value = parser.string();
                if (result.putIfAbsent(key, value) != null) {
                    throw malformed("duplicate JSON object key");
                }
            } while (parser.consume(','));
        }
        parser.expect('}');
        parser.end();
        return Map.copyOf(result);
    }

    public List<String> readStringList(String json) {
        Parser parser = new Parser(json);
        List<String> result = new ArrayList<>();
        parser.expect('[');
        if (!parser.peek(']')) {
            do {
                result.add(parser.string());
            } while (parser.consume(','));
        }
        parser.expect(']');
        parser.end();
        return List.copyOf(result);
    }

    private static String quote(String value) {
        if (value == null) {
            throw malformed("JSON string value must not be null");
        }
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append("\\u").append(String.format("%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static DataIntegrityViolationException malformed(String reason) {
        return new DataIntegrityViolationException("invalid restricted persistence JSON: " + reason);
    }

    private static final class Parser {
        private final String source;
        private int offset;

        private Parser(String source) {
            if (source == null) {
                throw malformed("value is null");
            }
            this.source = source;
        }

        private String string() {
            whitespace();
            if (offset >= source.length() || source.charAt(offset++) != '"') {
                throw malformed("string expected");
            }
            StringBuilder result = new StringBuilder();
            while (offset < source.length()) {
                char character = source.charAt(offset++);
                if (character == '"') {
                    return result.toString();
                }
                if (character != '\\') {
                    if (character < 0x20) {
                        throw malformed("unescaped control character");
                    }
                    result.append(character);
                    continue;
                }
                if (offset >= source.length()) {
                    throw malformed("truncated escape");
                }
                char escape = source.charAt(offset++);
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicode());
                    default -> throw malformed("unsupported escape");
                }
            }
            throw malformed("unterminated string");
        }

        private char unicode() {
            if (offset + 4 > source.length()) {
                throw malformed("truncated unicode escape");
            }
            try {
                char result = (char) Integer.parseInt(source.substring(offset, offset + 4), 16);
                offset += 4;
                return result;
            } catch (NumberFormatException exception) {
                throw malformed("invalid unicode escape");
            }
        }

        private void expect(char expected) {
            whitespace();
            if (offset >= source.length() || source.charAt(offset++) != expected) {
                throw malformed("expected '" + expected + "'");
            }
        }

        private boolean consume(char expected) {
            whitespace();
            if (offset < source.length() && source.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            whitespace();
            return offset < source.length() && source.charAt(offset) == expected;
        }

        private void end() {
            whitespace();
            if (offset != source.length()) {
                throw malformed("trailing content");
            }
        }

        private void whitespace() {
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
        }
    }
}
