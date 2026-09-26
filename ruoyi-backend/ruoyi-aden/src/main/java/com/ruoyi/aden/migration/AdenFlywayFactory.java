package com.ruoyi.aden.migration;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/** Aden migration 的固定构造入口，避免 location、历史表和 baseline 策略漂移。 */
public final class AdenFlywayFactory {
    public static final String LOCATION = "classpath:db/aden-migration";
    public static final String HISTORY_TABLE = "aden_flyway_schema_history";
    public static final String BASELINE_VERSION = "0";
    public static final String EXPECTED_VERSION = "5";

    private AdenFlywayFactory() {
    }

    public static Flyway create(DataSource dataSource) {
        return configure(Flyway.configure().dataSource(dataSource)).load();
    }

    public static Flyway create(String url, String username, String password) {
        return configure(Flyway.configure().dataSource(url, username, password)).load();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration configure(
            org.flywaydb.core.api.configuration.FluentConfiguration configuration) {
        return configuration
                .locations(LOCATION)
                .table(HISTORY_TABLE)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .baselineVersion(BASELINE_VERSION)
                .baselineDescription("Existing RuoYi schema before Aden");
    }
}
