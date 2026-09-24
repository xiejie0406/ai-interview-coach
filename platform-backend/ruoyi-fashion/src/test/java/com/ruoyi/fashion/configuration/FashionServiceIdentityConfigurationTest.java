package com.ruoyi.fashion.configuration;

import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceAuthFailure;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceAuthenticationException;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentity;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceRequest;
import com.ruoyi.fashion.infrastructure.airuntime.security.InMemoryFashionServiceReplayStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FashionServiceIdentityConfigurationTest {

    private final FashionServiceIdentityConfiguration configuration =
            new FashionServiceIdentityConfiguration();

    @Test
    void absentSecretKeepsBoundaryUnavailableInsteadOfUsingADefault() {
        FashionServiceIdentity identity = configuration.fashionServiceIdentity(
                new FashionServiceIdentityProperties(),
                new InMemoryFashionServiceReplayStore());

        assertThat(identity.configured()).isFalse();
        assertThatThrownBy(() -> identity.sign(request()))
                .isInstanceOfSatisfying(FashionServiceAuthenticationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(FashionServiceAuthFailure.NOT_CONFIGURED));
    }

    @Test
    void partialOrWeakSecretConfigurationStopsStartup() {
        FashionServiceIdentityProperties partial = new FashionServiceIdentityProperties();
        partial.setActiveKeyId("current");

        assertThatThrownBy(() -> configuration.fashionServiceIdentity(
                partial, new InMemoryFashionServiceReplayStore()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("配置不完整");

        FashionServiceIdentityProperties weak = new FashionServiceIdentityProperties();
        weak.setActiveKeyId("current");
        weak.setActiveKeyBase64(Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> configuration.fashionServiceIdentity(
                weak, new InMemoryFashionServiceReplayStore()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    void externallyInjectedCurrentAndPreviousKeysCreateConfiguredIdentity() {
        FashionServiceIdentityProperties properties = new FashionServiceIdentityProperties();
        properties.setActiveKeyId("current");
        properties.setActiveKeyBase64(encoded("0123456789abcdef0123456789abcdef"));
        properties.setPreviousKeyId("previous");
        properties.setPreviousKeyBase64(encoded("abcdef0123456789abcdef0123456789"));

        FashionServiceIdentity identity = configuration.fashionServiceIdentity(
                properties, new InMemoryFashionServiceReplayStore());

        assertThat(identity.configured()).isTrue();
        assertThat(identity.sign(request()).keyId()).isEqualTo("current");
    }

    @Test
    void rejectsReusingTheSameKeyIdForBothRotationSlots() {
        FashionServiceIdentityProperties properties = new FashionServiceIdentityProperties();
        properties.setActiveKeyId("same");
        properties.setActiveKeyBase64(encoded("0123456789abcdef0123456789abcdef"));
        properties.setPreviousKeyId("same");
        properties.setPreviousKeyBase64(encoded("abcdef0123456789abcdef0123456789"));

        assertThatThrownBy(() -> configuration.fashionServiceIdentity(
                properties, new InMemoryFashionServiceReplayStore()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不同 keyId");
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static FashionServiceRequest request() {
        return new FashionServiceRequest("POST", "/internal/v1/test", new byte[0]);
    }
}
