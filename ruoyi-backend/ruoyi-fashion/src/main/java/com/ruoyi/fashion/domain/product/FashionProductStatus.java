package com.ruoyi.fashion.domain.product;

import java.util.Locale;

public enum FashionProductStatus {
    DRAFT("draft"),
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String code;

    FashionProductStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static FashionProductStatus fromCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (FashionProductStatus status : values()) {
            if (status.code.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("商品状态必须是 draft、active 或 inactive");
    }
}
