package com.ruoyi.aden.migration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenMigrationCliTest {
    @Test
    void parsesOnlyFixedSafeOptionsAndReadsPasswordFromEnvironment() {
        AdenMigrationCli.Arguments arguments = AdenMigrationCli.Arguments.parse(new String[] {
                "migrate",
                "--url=jdbc:mysql://127.0.0.1:3306/ruoyi",
                "--username=aden",
                "--expected-database=ruoyi",
                "--password-env=TEST_PASSWORD"
        }, name -> Map.of("TEST_PASSWORD", "secret").get(name));

        assertEquals(AdenMigrationCli.Command.MIGRATE, arguments.command());
        assertEquals("secret", arguments.password());
        assertEquals("ruoyi", arguments.expectedDatabase());
        assertEquals(false, arguments.toString().contains("secret"));
    }

    @Test
    void rejectsLocationOverrideAndNonMysqlUrl() {
        assertThrows(IllegalArgumentException.class, () -> AdenMigrationCli.Arguments.parse(new String[] {
                "migrate", "--url=jdbc:postgresql://127.0.0.1/db", "--username=x",
                "--expected-database=db", "--location=classpath:anywhere"
        }, name -> "secret"));
    }

    @Test
    void requiresExplicitPasswordEnvironmentVariable() {
        assertThrows(IllegalArgumentException.class, () -> AdenMigrationCli.Arguments.parse(new String[] {
                "validate", "--url=jdbc:mysql://127.0.0.1:3306/ruoyi", "--username=x",
                "--expected-database=ruoyi"
        }, name -> null));
    }
}
