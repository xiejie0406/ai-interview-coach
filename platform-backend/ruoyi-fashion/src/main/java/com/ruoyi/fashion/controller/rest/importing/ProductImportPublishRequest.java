package com.ruoyi.fashion.controller.rest.importing;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class ProductImportPublishRequest {
    public long rowVersion;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("发布请求包含未知字段：" + field);
    }
}
