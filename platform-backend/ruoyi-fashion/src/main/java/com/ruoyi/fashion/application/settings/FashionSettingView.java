package com.ruoyi.fashion.application.settings;

/** Admin 设置页使用的非敏感类型化视图。 */
public record FashionSettingView(
        String key,
        String name,
        String description,
        FashionSettingType type,
        String value,
        String defaultValue,
        String dictionaryType) {
}
