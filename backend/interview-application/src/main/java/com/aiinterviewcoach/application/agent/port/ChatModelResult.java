package com.aiinterviewcoach.application.agent.port;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 结构化成功或稳定失败；坏 JSON/schema 不得伪装成成功。 */
public sealed interface ChatModelResult permits ChatModelResult.Success, ChatModelResult.Failure {

    record Success(
            Map<String, Object> structuredOutput,
            ModelUsage usage,
            Optional<String> providerRequestIdHash
    ) implements ChatModelResult {
        public Success {
            structuredOutput = immutableObject(
                    DomainPreconditions.requireNonNull(structuredOutput, "structuredOutput"));
            DomainPreconditions.requireNonNull(usage, "modelUsage");
            providerRequestIdHash = providerRequestIdHash == null ? Optional.empty() : providerRequestIdHash;
        }

        @Override
        public String toString() {
            return "Success[structuredOutput=<redacted>, usage=" + usage
                    + ", providerRequestIdHash=" + providerRequestIdHash + "]";
        }
    }

    record Failure(ProviderFailure failure) implements ChatModelResult {
        public Failure {
            DomainPreconditions.requireNonNull(failure, "providerFailure");
        }
    }

    private static Map<String, Object> immutableObject(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                DomainPreconditions.requireText(key, "structuredOutputKey"), immutableValue(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object immutableValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                DomainPreconditions.require(key instanceof String, DomainErrorCode.INVALID_ARGUMENT,
                        "structured output object keys must be strings");
                String textKey = DomainPreconditions.requireText((String) key, "structuredOutputKey");
                typed.put(textKey, immutableValue(nested));
            });
            return Collections.unmodifiableMap(typed);
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            list.forEach(item -> copied.add(immutableValue(item)));
            return Collections.unmodifiableList(copied);
        }
        throw new com.aiinterviewcoach.domain.platform.DomainException(
                DomainErrorCode.INVALID_ARGUMENT,
                "structured output contains a non-JSON value");
    }
}
