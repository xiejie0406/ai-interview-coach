package com.ruoyi.fashion.configuration.telemetry;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 只装配 API 基座；是否配置 SDK、Collector 或 Exporter 由部署环境决定。 */
@FashionModuleEnabled
@Configuration(proxyBeanMethods = false)
public class FashionTelemetryConfiguration {

    @Bean(name = "fashionTelemetry")
    @ConditionalOnMissingBean(FashionTelemetry.class)
    public FashionTelemetry fashionTelemetry(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable(OpenTelemetry::noop);
        return new FashionTelemetry(openTelemetry);
    }
}
