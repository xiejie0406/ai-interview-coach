package com.ruoyi.interview.application.security;

import java.util.Set;

/**
 * AI 业务层使用的只读登录主体值对象。
 * 不承载密码、Token 原文或可变的认证状态。
 */
public record PrincipalRef(
        Long userId,
        Long deptId,
        String username,
        Set<String> permissions) {

    public PrincipalRef {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("RuoYi userId must be positive");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
