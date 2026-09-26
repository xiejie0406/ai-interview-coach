package com.ruoyi.fashion.application.product.port;

public record ProductSearchCriteria(
        String sourceCode,
        String categoryCode,
        String status,
        String keyword,
        int offset,
        int limit) {

    public ProductSearchCriteria {
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("分页参数无效");
        }
    }
}
