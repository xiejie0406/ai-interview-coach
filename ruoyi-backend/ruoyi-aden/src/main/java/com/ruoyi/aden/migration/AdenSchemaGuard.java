package com.ruoyi.aden.migration;

import com.ruoyi.aden.configuration.AdenProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import javax.sql.DataSource;

/** 正常应用启动时只读验证 Aden schema；不执行 baseline、migrate 或 repair。 */
public final class AdenSchemaGuard implements ApplicationRunner {
    private final DataSource dataSource;
    private final AdenProperties.Schema schema;

    public AdenSchemaGuard(DataSource dataSource, AdenProperties.Schema schema) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        AdenDatabasePreconditions.verifyCurrentSchema(dataSource, schema.getExpectedDatabase());
        Flyway flyway = AdenFlywayFactory.create(dataSource);
        flyway.validate();
        requireVersion(flyway, Integer.toString(schema.getExpectedVersion()));
    }

    static void requireVersion(Flyway flyway, String expectedVersion) {
        MigrationInfo current = flyway.info().current();
        String actual = current == null || current.getVersion() == null ? null : current.getVersion().getVersion();
        if (!expectedVersion.equals(actual)) {
            throw new IllegalStateException(
                    "Aden schema 版本不满足：expected=" + expectedVersion + ", actual=" + actual);
        }
    }
}
