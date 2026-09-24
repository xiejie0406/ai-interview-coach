package com.ruoyi.fashion.application.settings;

/** 设置更新结果也作为平台操作日志的 before/after 证据。 */
public record FashionSettingChange(String key, String beforeValue, String afterValue, String operator) {
}
