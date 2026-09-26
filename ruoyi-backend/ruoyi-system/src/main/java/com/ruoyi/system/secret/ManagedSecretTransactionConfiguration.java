package com.ruoyi.system.secret;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** 三模块写操作固定使用若依主 MySQL，不与面试 PostgreSQL 事务管理器混用。 */
@Configuration(proxyBeanMethods = false)
public class ManagedSecretTransactionConfiguration {
    @Bean("managedSecretTransactionManager")
    public PlatformTransactionManager managedSecretTransactionManager(
            @Qualifier("dynamicDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
