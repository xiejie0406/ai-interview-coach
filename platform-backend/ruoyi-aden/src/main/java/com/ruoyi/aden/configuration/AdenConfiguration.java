package com.ruoyi.aden.configuration;

import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.infrastructure.id.UuidAdenIdGenerator;
import com.ruoyi.aden.migration.AdenSchemaGuard;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;

/** Aden 组合根；默认关闭业务能力，仅注册无副作用的基础设施。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdenProperties.class)
public class AdenConfiguration {
    @Bean(name = "adenClock")
    public Clock adenClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "adenTransactionManager")
    public PlatformTransactionManager adenTransactionManager(
            @Qualifier("dynamicDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public AdenIdGenerator adenIdGenerator() {
        return new UuidAdenIdGenerator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true")
    public ApplicationRunner adenSchemaGuard(
            @Qualifier("dynamicDataSource") DataSource dataSource,
            AdenProperties properties) {
        return new AdenSchemaGuard(dataSource, properties.getSchema());
    }
}
