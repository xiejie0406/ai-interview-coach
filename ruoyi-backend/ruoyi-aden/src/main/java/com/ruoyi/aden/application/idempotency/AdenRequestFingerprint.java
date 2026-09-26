package com.ruoyi.aden.application.idempotency;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 对 JSON 对象键递归排序后计算 SHA-256；数组顺序保留为业务语义。 */
public final class AdenRequestFingerprint {
    private final ObjectMapper objectMapper;

    public AdenRequestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String hashJson(String json) {
        Objects.requireNonNull(json, "json");
        try {
            return hashNode(objectMapper.readTree(json));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法解析幂等请求 JSON", exception);
        }
    }

    public String hashValue(Object value) {
        Objects.requireNonNull(value, "value");
        return hashNode(objectMapper.valueToTree(value));
    }

    public String canonicalJson(Object value) {
        Objects.requireNonNull(value, "value");
        return writeCanonical(objectMapper.valueToTree(value));
    }

    private String hashNode(JsonNode node) {
        String canonical = writeCanonical(node);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private String writeCanonical(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(canonicalize(node));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化规范化幂等请求", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) return node;
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            names.addAll(node.propertyNames());
            names.sort(Comparator.naturalOrder());
            names.forEach(name -> result.set(name, canonicalize(node.get(name))));
            return result;
        }
        throw new IllegalArgumentException("幂等请求必须是合法 JSON 值");
    }
}
