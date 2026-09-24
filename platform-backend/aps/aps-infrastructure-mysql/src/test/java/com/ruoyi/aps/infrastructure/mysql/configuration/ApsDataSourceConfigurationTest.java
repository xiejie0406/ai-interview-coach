package com.ruoyi.aps.infrastructure.mysql.configuration;

import javax.sql.DataSource;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class ApsDataSourceConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApsDataSourceConfiguration.class);

    @Test
    void remainsDisabledByDefault()
    {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("apsDataSource"));
    }

    @Test
    void requiresAllThreePersistenceGates()
    {
        contextRunner.withPropertyValues(
                "aps.enabled=true",
                "aps.persistence.enabled=true",
                "aps.datasource.enabled=true",
                "aps.datasource.url=jdbc:mysql://127.0.0.1:1/never_connect",
                "aps.datasource.username=aps")
                .run(context -> {
                    assertThat(context).hasBean("apsDataSource");
                    assertThat(context).hasBean("apsSqlSessionFactory");
                    assertThat(context).hasBean("apsTransactionManager");
                    assertThat(context).hasSingleBean(ApsTransactionOperations.class);
                    assertThat(context.getBean("apsDataSource")).isInstanceOf(DataSource.class);
                    assertThat(context.getBean("apsSqlSessionFactory")).isInstanceOf(SqlSessionFactory.class);
                    assertThat(context.getBean("apsTransactionManager")).isInstanceOf(PlatformTransactionManager.class);
                });
    }

    @Test
    void failsClosedInsteadOfFallingBackToRuoyiDataSource()
    {
        contextRunner.withPropertyValues(
                "aps.enabled=true",
                "aps.persistence.enabled=true",
                "aps.datasource.enabled=true",
                "aps.datasource.username=aps")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("APS_DB_URL");
                });
    }
}
