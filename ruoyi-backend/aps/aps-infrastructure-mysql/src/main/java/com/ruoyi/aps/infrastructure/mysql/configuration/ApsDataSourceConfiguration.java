package com.ruoyi.aps.infrastructure.mysql.configuration;

import java.time.Instant;
import javax.sql.DataSource;
import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.domain.shared.ApsId;
import com.ruoyi.aps.infrastructure.mysql.typehandler.ApsEnumTypeHandler;
import com.ruoyi.aps.infrastructure.mysql.typehandler.ApsIdTypeHandler;
import com.ruoyi.aps.infrastructure.mysql.typehandler.UtcInstantTypeHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * APS 独立 Druid/MyBatis/事务装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApsDataSourceProperties.class)
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.datasource", name = "enabled", havingValue = "true")
@MapperScan(basePackages = "com.ruoyi.aps.infrastructure.mysql.mapper",
        sqlSessionFactoryRef = "apsSqlSessionFactory")
public class ApsDataSourceConfiguration
{
    @Bean(name = "apsDataSource", destroyMethod = "close")
    public DataSource apsDataSource(ApsDataSourceProperties properties)
    {
        properties.validate();
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setName("apsDataSource");
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setConnectTimeout(properties.getConnectTimeoutMs());
        dataSource.setSocketTimeout(properties.getSocketTimeoutMs());
        dataSource.setInitialSize(properties.getInitialSize());
        dataSource.setMinIdle(properties.getMinIdle());
        dataSource.setMaxActive(properties.getMaxActive());
        // DATETIME 按 UTC 映射；数据库默认时间和 NOW() 必须使用同一时区。
        dataSource.setConnectionInitSqls(java.util.List.of("SET time_zone = '+00:00'"));
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestWhileIdle(true);
        return dataSource;
    }

    @Bean(name = "apsSqlSessionFactory")
    public SqlSessionFactory apsSqlSessionFactory(@Qualifier("apsDataSource") DataSource dataSource) throws Exception
    {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(Instant.class, UtcInstantTypeHandler.class);
        configuration.getTypeHandlerRegistry().register(ApsId.class, ApsIdTypeHandler.class);
        configuration.getTypeHandlerRegistry().setDefaultEnumTypeHandler(ApsEnumTypeHandler.class);

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/aps/**/*.xml"));
        return factory.getObject();
    }

    @Bean(name = "apsTransactionManager")
    public PlatformTransactionManager apsTransactionManager(@Qualifier("apsDataSource") DataSource dataSource)
    {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public ApsTransactionOperations apsTransactionOperations(
            @Qualifier("apsTransactionManager") PlatformTransactionManager transactionManager)
    {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return new SpringApsTransactionOperations(template);
    }
}
