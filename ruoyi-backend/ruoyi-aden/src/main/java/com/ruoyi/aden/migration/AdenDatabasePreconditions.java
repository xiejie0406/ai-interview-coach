package com.ruoyi.aden.migration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/** 在任何迁移动作前验证目标库名和 RuoYi 标志表，防止连接错库。 */
public final class AdenDatabasePreconditions {
    public static final String RUOYI_MARKER_TABLE = "sys_menu";
    public static final Set<String> EXPECTED_BUSINESS_TABLES = Set.of(
            "aden_workspace", "aden_workspace_member", "aden_task", "aden_task_step",
            "aden_runner", "aden_runner_credential", "aden_runner_session", "aden_runner_delivery",
            "aden_event", "aden_outbox", "aden_inbox", "aden_audit_event",
            "aden_collection_item", "aden_collection_snapshot", "aden_collection_manifest",
            "aden_collection_curation", "aden_collection_upload", "aden_collection_export");

    private AdenDatabasePreconditions() {
    }

    public static void verify(DataSource dataSource, String expectedDatabase) {
        try (Connection connection = dataSource.getConnection()) {
            verify(connection, expectedDatabase);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法校验 Aden 目标数据库", exception);
        }
    }

    /** 启动 guard / validate 的强校验：18 张业务表必须与当前版本完全一致。 */
    public static void verifyCurrentSchema(DataSource dataSource, String expectedDatabase) {
        try (Connection connection = dataSource.getConnection()) {
            verify(connection, expectedDatabase);
            verifyCurrentTables(connection, requireExpectedDatabase(expectedDatabase));
        } catch (SQLException exception) {
            throw new IllegalStateException("无法校验 Aden 当前 Schema", exception);
        }
    }

    public static void verifyCurrentTables(Connection connection, String expectedDatabase) {
        try {
            Set<String> tables = adenBusinessTables(connection, requireExpectedDatabase(expectedDatabase));
            if (!tables.equals(EXPECTED_BUSINESS_TABLES)) {
                Set<String> missing = new LinkedHashSet<>(EXPECTED_BUSINESS_TABLES);
                missing.removeAll(tables);
                Set<String> unexpected = new LinkedHashSet<>(tables);
                unexpected.removeAll(EXPECTED_BUSINESS_TABLES);
                throw new IllegalStateException(
                        "Aden 业务表集合不匹配：missing=" + missing + ", unexpected=" + unexpected);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取 Aden 业务表集合", exception);
        }
    }

    public static void verify(Connection connection, String expectedDatabase) {
        String expected = requireExpectedDatabase(expectedDatabase);
        try {
            String actual;
            try (PreparedStatement statement = connection.prepareStatement("select database()");
                 ResultSet result = statement.executeQuery()) {
                actual = result.next() ? result.getString(1) : null;
            }
            if (!expected.equals(actual)) {
                throw new IllegalStateException("拒绝操作非预期数据库：expected=" + expected + ", actual=" + actual);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    select count(*)
                      from information_schema.tables
                     where table_schema = ? and table_name = ?
                    """)) {
                statement.setString(1, expected);
                statement.setString(2, RUOYI_MARKER_TABLE);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IllegalStateException("拒绝操作缺少 RuoYi 标志表 sys_menu 的数据库 " + expected);
                    }
                }
            }
            rejectUnversionedAdenTables(connection, expected);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法校验 Aden 目标数据库", exception);
        }
    }

    private static void rejectUnversionedAdenTables(Connection connection, String database) throws SQLException {
        boolean historyExists;
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*) from information_schema.tables
                 where table_schema = ? and table_name = ?
                """)) {
            statement.setString(1, database);
            statement.setString(2, AdenFlywayFactory.HISTORY_TABLE);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                historyExists = result.getInt(1) == 1;
            }
        }
        Set<String> tables = adenBusinessTables(connection, database);
        Set<String> unexpected = new LinkedHashSet<>(tables);
        unexpected.removeAll(EXPECTED_BUSINESS_TABLES);
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException("拒绝操作含未知 aden_* 业务表的数据库：" + unexpected);
        }
        if (!tables.isEmpty() && !historyExists) {
            throw new IllegalStateException(
                    "拒绝操作存在未纳入 Aden Flyway 历史的 aden_* 表的数据库 " + database);
        }
    }

    private static Set<String> adenBusinessTables(Connection connection, String database) throws SQLException {
        Set<String> tables = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select table_name from information_schema.tables
                 where table_schema = ? and left(table_name, 5) = 'aden_' and table_name <> ?
                 order by table_name
                """)) {
            statement.setString(1, database);
            statement.setString(2, AdenFlywayFactory.HISTORY_TABLE);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) tables.add(result.getString(1));
            }
        }
        return tables;
    }

    static String requireExpectedDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("expected database 不能为空");
        }
        return value.trim();
    }
}
