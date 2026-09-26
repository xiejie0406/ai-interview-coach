package com.ruoyi.aden.configuration;

import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.infrastructure.id.UuidAdenIdGenerator;
import com.ruoyi.aden.migration.AdenSchemaGuard;
import org.mybatis.spring.annotation.MapperScan;
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

/** Aden 组合根；关闭模块时不注册业务组件或 Mapper。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AdenProperties.class)
@MapperScan(basePackages = "com.ruoyi.aden.infrastructure.persistence.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
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
    public ApplicationRunner adenSchemaGuard(
            @Qualifier("dynamicDataSource") DataSource dataSource,
            AdenProperties properties) {
        return new AdenSchemaGuard(dataSource, properties.getSchema());
    }
}
