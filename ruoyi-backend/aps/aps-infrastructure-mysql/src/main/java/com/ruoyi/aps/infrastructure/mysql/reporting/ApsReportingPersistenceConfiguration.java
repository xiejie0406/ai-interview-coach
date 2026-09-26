package com.ruoyi.aps.infrastructure.mysql.reporting;

import com.ruoyi.aps.application.reporting.ReportingRepository;
import com.ruoyi.aps.application.reporting.ReportingService;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsReportingMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApsReportingProperties.class)
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.datasource", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.reporting", name = "enabled", havingValue = "true")
public class ApsReportingPersistenceConfiguration
{
    @Bean public ReportingRepository apsReportingRepository(ApsReportingMapper mapper)
    {
        return new MybatisReportingRepository(mapper);
    }

    @Bean public ReportingService apsReportingService(ReportingRepository repository,
            ApsReportingProperties properties)
    {
        properties.validate();
        return new ReportingService(repository, properties.getCode(), properties.parsedZoneId());
    }
}
