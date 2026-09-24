package com.ruoyi.fashion.configuration.persistence;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 只有显式开启时才执行；已有 RuoYi 非空库的首次 history baseline 还需第二个批准开关。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FashionMigrationProperties.class)
@ConditionalOnProperty(prefix = "fashion.migration", name = "enabled", havingValue = "true")
public class FashionMigrationConfiguration {

    @Bean(name = "fashionFlyway")
    public Flyway fashionFlyway(@Qualifier("dynamicDataSource") DataSource dataSource) {
        return FashionFlywayFactory.create(dataSource);
    }

    @Bean
    public InitializingBean fashionMigrationRunner(
            @Qualifier("dynamicDataSource") DataSource dataSource,
            @Qualifier("fashionFlyway") Flyway flyway,
            FashionMigrationProperties properties) {
        return () -> {
            FashionDatabasePreconditions.Inspection inspection =
                    FashionDatabasePreconditions.inspect(dataSource, properties.getExpectedDatabase());
            if (!inspection.historyExists()) {
                if (!properties.isBaselineApproved()) {
                    throw new IllegalStateException(
                            "Fashion 首次迁移需要显式设置 FASHION_MIGRATION_BASELINE_APPROVED=true");
                }
                flyway.baseline();
            }
            flyway.migrate();
            FashionDatabasePreconditions.verifyCurrentSchema(dataSource, properties.getExpectedDatabase());
        };
    }
}
