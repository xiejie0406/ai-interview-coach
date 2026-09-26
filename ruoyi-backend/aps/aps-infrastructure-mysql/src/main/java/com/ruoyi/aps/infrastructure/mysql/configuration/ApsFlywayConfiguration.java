package com.ruoyi.aps.infrastructure.mysql.configuration;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只针对 APS 独立数据源运行的 Flyway 装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApsFlywayProperties.class)
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.datasource", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.flyway", name = "enabled", havingValue = "true")
public class ApsFlywayConfiguration
{
    @Bean(name = "apsFlyway")
    public Flyway apsFlyway(@Qualifier("apsDataSource") DataSource dataSource, ApsFlywayProperties properties)
    {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(properties.getLocations())
                .table(properties.getTable())
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();
    }

    @Bean
    public InitializingBean apsFlywayMigrationInitializer(@Qualifier("apsFlyway") Flyway flyway)
    {
        return () -> {
            flyway.migrate();
            flyway.validate();
        };
    }
}
