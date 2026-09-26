package com.ruoyi.fashion.controller.rest.product;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class ProductBulkUpdateRequest {
    public List<ProductPatchRequest> products;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("批量商品请求包含未知字段：" + field);
    }
}
