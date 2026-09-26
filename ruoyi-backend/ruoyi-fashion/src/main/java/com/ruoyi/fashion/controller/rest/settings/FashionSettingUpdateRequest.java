package com.ruoyi.fashion.controller.rest.settings;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 设置更新只接收注册键和值，不接受人员范围、Secret 或任意扩展字段。 */
public record FashionSettingUpdateRequest(
        @NotBlank @Size(max = 100) String key,
        @NotBlank @Size(max = 500) String value) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignoredValue) {
        throw new IllegalArgumentException("设置请求包含不允许的字段：" + field);
    }
}
