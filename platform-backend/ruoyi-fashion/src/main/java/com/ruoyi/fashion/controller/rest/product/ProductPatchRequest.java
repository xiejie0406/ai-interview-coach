package com.ruoyi.fashion.controller.rest.product;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ruoyi.fashion.application.product.ProductPatch;

public class ProductPatchRequest {
    public String id;
    public long rowVersion;
    public Map<String, Object> changes;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("批量修改包含未知字段：" + field);
    }

    ProductPatch toCommand() {
        return new ProductPatch(id, rowVersion, changes);
    }
}
