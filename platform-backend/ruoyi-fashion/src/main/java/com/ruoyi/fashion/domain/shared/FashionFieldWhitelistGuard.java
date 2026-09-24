package com.ruoyi.fashion.domain.shared;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 拒绝批量映射或动态配置中的任意字段写入。 */
public final class FashionFieldWhitelistGuard {
    private FashionFieldWhitelistGuard() {
    }

    public static void requireOnlyAllowed(Map<String, ?> values, Set<String> allowed) {
        Set<String> rejected = new LinkedHashSet<>(values.keySet());
        rejected.removeAll(allowed);
        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException("包含不允许写入的字段：" + rejected);
        }
    }
}
