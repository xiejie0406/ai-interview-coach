package com.ruoyi.interview.configuration;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** PostgreSQL Flyway 的唯一归属；MySQL RuoYi 初始化仍由平台脚本管理。 */
@InterviewEnabled
@Configuration
@ConditionalOnProperty(prefix = "interview.flyway", name = "enabled", havingValue = "true")
public class InterviewFlywayConfiguration {
    @Bean(name = "interviewFlyway", initMethod = "migrate")
    public Flyway interviewFlyway(@Qualifier("interviewDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("platform")
                .defaultSchema("platform")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("7")
                .baselineDescription("Existing platform schema through V7")
                .cleanDisabled(true)
                .load();
    }

}
