package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.inbound.rest.common.ApiErrorResponseWriter;
import com.aiinterviewcoach.adapters.inbound.rest.common.CorrelationIdFilter;
import com.aiinterviewcoach.adapters.inbound.rest.health.ServiceMetadata;
import com.aiinterviewcoach.boot.properties.ReleaseProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfiguration {
    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    ApiErrorResponseWriter apiErrorResponseWriter() {
        return new ApiErrorResponseWriter();
    }

    @Bean
    ServiceMetadata serviceMetadata(ReleaseProperties releaseProperties) {
        return new ServiceMetadata() {
            @Override
            public String serviceName() {
                return "ai-interview-coach";
            }

            @Override
            public String releaseVersion() {
                return releaseProperties.getVersion();
            }
        };
    }
}
