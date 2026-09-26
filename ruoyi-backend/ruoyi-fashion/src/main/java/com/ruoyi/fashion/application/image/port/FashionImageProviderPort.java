package com.ruoyi.fashion.application.image.port;

import java.math.BigDecimal;

import tools.jackson.databind.JsonNode;

/** Provider Adapter 不接收客户、价格、库存或内部备注。 */
public interface FashionImageProviderPort {
    boolean available();

    ProviderResult submit(String requestKey, String imageType, JsonNode inputs, JsonNode parameters, int count);

    ProviderResult query(String providerTaskId);

    default void cancel(String providerTaskId) {
    }

    record ProviderResult(
            String status,
            String providerCode,
            String providerTaskId,
            JsonNode results,
            BigDecimal actualCost,
            String currency,
            String errorCode,
            String errorMessage,
            boolean retryable) {
    }
}
