package com.ruoyi.fashion.application.product;

import java.util.Map;

public record ProductPatch(String id, long rowVersion, Map<String, Object> changes) {
}
