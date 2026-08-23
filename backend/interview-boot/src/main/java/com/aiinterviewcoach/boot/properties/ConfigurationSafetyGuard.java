package com.aiinterviewcoach.boot.properties;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校验会被标准 Spring 环境变量覆盖的关键属性，避免绕过自定义配置属性红线。
 * 这里只读取布尔值和枚举，不读取或记录任何凭据值。
 */
public final class ConfigurationSafetyGuard implements InitializingBean {
    private final Environment environment;

    public ConfigurationSafetyGuard(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Override
    public void afterPropertiesSet() {
        requireTrue("server.servlet.session.cookie.secure");
        requireTrue("server.servlet.session.cookie.http-only");
        requireTrue("spring.flyway.clean-disabled");
        requireFalse("spring.servlet.multipart.enabled");
        requireExact("spring.jpa.hibernate.ddl-auto", "validate");
        requireExact("server.error.include-message", "never");
        requireExact("server.error.include-stacktrace", "never");
        requireExact("management.endpoint.health.show-details", "never");
        requireApprovedSameSite();
        requireHealthOnlyActuatorExposure();
    }

    private void requireTrue(String key) {
        if (!environment.getProperty(key, Boolean.class, false)) {
            throw unsafe(key);
        }
    }

    private void requireFalse(String key) {
        if (environment.getProperty(key, Boolean.class, true)) {
            throw unsafe(key);
        }
    }

    private void requireExact(String key, String expected) {
        String actual = environment.getProperty(key, "");
        if (!expected.equalsIgnoreCase(actual)) {
            throw unsafe(key);
        }
    }

    private void requireApprovedSameSite() {
        String actual = environment.getProperty("server.servlet.session.cookie.same-site", "")
                .toLowerCase(Locale.ROOT);
        if (!actual.equals("strict") && !actual.equals("lax")) {
            throw unsafe("server.servlet.session.cookie.same-site");
        }
    }

    private void requireHealthOnlyActuatorExposure() {
        Set<String> exposed = Arrays.stream(environment
                        .getProperty("management.endpoints.web.exposure.include", "")
                        .split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (!exposed.equals(Set.of("health"))) {
            throw unsafe("management.endpoints.web.exposure.include");
        }
    }

    private IllegalStateException unsafe(String key) {
        return new IllegalStateException("unsafe foundation configuration: " + key);
    }
}
