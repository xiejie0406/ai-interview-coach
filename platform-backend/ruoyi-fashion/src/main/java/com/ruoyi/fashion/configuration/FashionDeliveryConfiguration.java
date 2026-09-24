package com.ruoyi.fashion.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FashionDeliveryProperties.class)
@EnableScheduling
public class FashionDeliveryConfiguration {
}
