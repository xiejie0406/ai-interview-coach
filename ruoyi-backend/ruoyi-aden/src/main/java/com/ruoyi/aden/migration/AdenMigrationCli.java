package com.ruoyi.aden.migration;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Aden 唯一写 Schema 的短生命周期入口。
 *
 * <p>示例：{@code AdenMigrationCli migrate --url=jdbc:mysql://127.0.0.1:3306/ruoyi
 * --username=ruoyi --expected-database=ruoyi}；密码与主 MySQL 自举连接共用 RUOYI_DB_PASSWORD。</p>
 */
public final class AdenMigrationCli {
    private AdenMigrationCli() {
    }

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args, System::getenv);
        try (Connection connection = DriverManager.getConnection(parsed.url(), parsed.username(), parsed.password())) {
            AdenDatabasePreconditions.verify(connection, parsed.expectedDatabase());
        } catch (SQLException exception) {
            throw new IllegalStateException("无法连接 Aden migration 目标数据库", exception);
        }

        Flyway flyway = AdenFlywayFactory.create(parsed.url(), parsed.username(), parsed.password());
        switch (parsed.command()) {
            case BASELINE -> flyway.baseline();
            case MIGRATE -> {
                flyway.migrate();
                flyway.validate();
                AdenSchemaGuard.requireVersion(flyway, AdenFlywayFactory.EXPECTED_VERSION);
                try (Connection connection = DriverManager.getConnection(parsed.url(), parsed.username(), parsed.password())) {
                    AdenDatabasePreconditions.verifyCurrentTables(connection, parsed.expectedDatabase());
                } catch (SQLException exception) {
                    throw new IllegalStateException("无法回读 Aden migration 后的业务表", exception);
                }
            }
            case VALIDATE -> {
                flyway.validate();
                AdenSchemaGuard.requireVersion(flyway, AdenFlywayFactory.EXPECTED_VERSION);
                try (Connection connection = DriverManager.getConnection(parsed.url(), parsed.username(), parsed.password())) {
                    AdenDatabasePreconditions.verifyCurrentTables(connection, parsed.expectedDatabase());
                } catch (SQLException exception) {
                    throw new IllegalStateException("无法回读 Aden 当前业务表", exception);
                }
            }
        }
        System.out.println("Aden migration command completed: " + parsed.command().name().toLowerCase(Locale.ROOT));
    }

    enum Command { BASELINE, MIGRATE, VALIDATE }

    record Arguments(Command command, String url, String username, String password, String expectedDatabase) {
        @Override
        public String toString() {
            return "Arguments[command=" + command + ", url=" + url + ", username=" + username
                    + ", password=<redacted>, expectedDatabase=" + expectedDatabase + "]";
        }

        static Arguments parse(String[] args, Function<String, String> environment) {
            if (args.length == 0) throw usage("缺少 command");
            Command command;
            try {
                command = Command.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw usage("command 只允许 baseline、migrate 或 validate");
            }
            Map<String, String> options = new LinkedHashMap<>();
            for (int index = 1; index < args.length; index++) {
                String argument = args[index];
                int separator = argument.indexOf('=');
                if (!argument.startsWith("--") || separator < 3) throw usage("非法参数：" + argument);
                String key = argument.substring(2, separator);
                String value = argument.substring(separator + 1);
                if (options.putIfAbsent(key, value) != null) throw usage("重复参数：--" + key);
            }
            for (String key : options.keySet()) {
                if (!key.equals("url") && !key.equals("username") && !key.equals("expected-database")
                        && !key.equals("password-env")) {
                    throw usage("不支持参数：--" + key);
                }
            }
            String url = required(options, "url");
            if (!url.startsWith("jdbc:mysql://")) throw usage("--url 必须使用 jdbc:mysql://");
            String username = required(options, "username");
            String expectedDatabase = AdenDatabasePreconditions.requireExpectedDatabase(
                    required(options, "expected-database"));
            String passwordVariable = "RUOYI_DB_PASSWORD";
            if (options.containsKey("password-env")) {
                if (!"aden_e2e".equals(expectedDatabase)
                        || !"ADEN_E2E_DB_PASSWORD".equals(options.get("password-env"))) {
                    throw usage("--password-env 只允许 Aden 隔离合成测试使用");
                }
                passwordVariable = "ADEN_E2E_DB_PASSWORD";
            }
            String password = environment.apply(passwordVariable);
            if (password == null) throw usage("自举变量 " + passwordVariable + " 未设置");
            return new Arguments(command, url, username, password, expectedDatabase);
        }

        private static String required(Map<String, String> options, String key) {
            String value = options.get(key);
            if (value == null || value.isBlank()) throw usage("缺少 --" + key);
            return value.trim();
        }

        private static IllegalArgumentException usage(String problem) {
            return new IllegalArgumentException(problem + System.lineSeparator()
                    + "usage: AdenMigrationCli <baseline|migrate|validate> "
                    + "--url=jdbc:mysql://HOST:PORT/DB --username=USER --expected-database=DB");
        }
    }
}
