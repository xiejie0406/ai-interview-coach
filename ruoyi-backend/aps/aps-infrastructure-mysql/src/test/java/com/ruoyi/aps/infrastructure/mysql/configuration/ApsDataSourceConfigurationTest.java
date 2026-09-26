package com.ruoyi.aps.infrastructure.mysql.configuration;

import javax.sql.DataSource;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class ApsDataSourceConfigurationTest
{
    @Test
    @EnabledIfEnvironmentVariable(named = "APS_UTC_TEST_DB_URL", matches = ".+")
    void databaseGeneratedAuditTimeUsesUtcEvenWhenServerUsesLocalTime() throws Exception
    {
        var properties = new ApsDataSourceProperties();
        properties.setEnabled(true);
        properties.setUrl(System.getenv("APS_UTC_TEST_DB_URL"));
        properties.setUsername(System.getenv("APS_UTC_TEST_DB_USERNAME"));
        properties.setPassword(System.getenv().getOrDefault("APS_UTC_TEST_DB_PASSWORD", ""));
        try (var source = (com.alibaba.druid.pool.DruidDataSource)
                new ApsDataSourceConfiguration().apsDataSource(properties);
                var connection = source.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TEMPORARY TABLE aps_utc_acceptance_probe "
                    + "(created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3))");
            statement.executeUpdate("INSERT INTO aps_utc_acceptance_probe () VALUES ()");
            try (var rows = statement.executeQuery("SELECT created_at, @@session.time_zone AS zone "
                    + "FROM aps_utc_acceptance_probe"))
            {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("zone")).isEqualTo("+00:00");
                var auditTime = new com.ruoyi.aps.infrastructure.mysql.typehandler.UtcInstantTypeHandler()
                        .getNullableResult(rows, "created_at");
                assertThat(java.time.Duration.between(auditTime, java.time.Instant.now()).abs().toSeconds())
                        .isLessThan(10);
            }
        }
    }

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

    @Test
    void failsWithBlankApsDatabaseUser()
    {
        contextRunner.withPropertyValues(
                "aps.enabled=true",
                "aps.persistence.enabled=true",
                "aps.datasource.enabled=true",
                "aps.datasource.url=jdbc:mysql://127.0.0.1:1/never_connect",
                "aps.datasource.username= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("APS_DB_USERNAME");
                });
    }
}
