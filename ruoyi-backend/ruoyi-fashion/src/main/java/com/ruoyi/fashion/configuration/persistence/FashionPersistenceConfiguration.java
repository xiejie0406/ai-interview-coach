package com.ruoyi.fashion.configuration.persistence;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fashion 持久化 Bean 的唯一装配入口，全部显式绑定 RuoYi MySQL dynamicDataSource。 */
@FashionModuleEnabled
@Configuration(proxyBeanMethods = false)
public class FashionPersistenceConfiguration {

    @Bean(name = "fashionJdbcTemplate")
    public NamedParameterJdbcTemplate fashionJdbcTemplate(
            @Qualifier("dynamicDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "fashionTransactionManager")
    public PlatformTransactionManager fashionTransactionManager(
            @Qualifier("dynamicDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "fashionTransactionTemplate")
    public TransactionTemplate fashionTransactionTemplate(
            @Qualifier("fashionTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
