package com.ruoyi.fashion.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class FashionModuleEnabledTest {

    @Test
    void defaultEnablesFashionComponents() {
        Set<String> candidates = candidates(Map.of());

        assertThat(candidates).contains(
                "com.ruoyi.fashion.controller.rest.product.FashionProductController",
                "com.ruoyi.fashion.configuration.persistence.FashionPersistenceConfiguration",
                "com.ruoyi.fashion.application.image.FashionQuoteImageWorker",
                "com.ruoyi.fashion.application.delivery.worker.FashionDeliveryWorker");
    }

    @Test
    void disablingModuleRemovesAllProductionComponentsFromScan() {
        assertThat(candidates(Map.of("fashion.enabled", "false"))).isEmpty();
    }

    private Set<String> candidates(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true, environment);
        return scanner.findCandidateComponents("com.ruoyi.fashion").stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(name -> name != null && !name.contains("Test$"))
                .collect(Collectors.toSet());
    }
}
