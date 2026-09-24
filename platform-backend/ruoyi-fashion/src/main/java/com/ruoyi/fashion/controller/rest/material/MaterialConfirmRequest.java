package com.ruoyi.fashion.controller.rest.material;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class MaterialConfirmRequest {
    public long rowVersion;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("图片确认请求包含未知字段：" + field);
    }
}
