package com.ruoyi.aden.application.security;

import java.util.Objects;
import java.util.Set;

public record AdenOperatorPrincipal(long userId, String username, Set<String> permissions) {
    private static final String ALL_PERMISSION = "*:*:*";

    public AdenOperatorPrincipal {
        if (userId <= 0) throw new IllegalArgumentException("userId 必须为正数");
        username = Objects.requireNonNull(username, "username");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(ALL_PERMISSION) || permissions.contains(permission);
    }
}
