package com.ruoyi.fashion.controller.rest.product;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class ProductStatusRequest {
    public String status;
    public long rowVersion;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("商品状态请求包含未知字段：" + field);
    }
}
