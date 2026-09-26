package com.ruoyi;

import com.ruoyi.system.secret.ManagedSecretCrypto;
import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

/** 首次登录前录入启动必需的固定用途；值只从交互式控制台读取。 */
final class ManagedSecretBootstrapCli {
    private static final String JWT = "platform.ruoyi.jwt";
    private static final String ADEN_PEPPER = "platform.aden.runner-pepper";
    private record Spec(String project, String capability, String mode, String name) { }
    private static final Map<String, Spec> ALLOWED = Map.of(
            JWT, new Spec("ruoyi", "jwt", "hmac", "若依 JWT 签名"),
            ADEN_PEPPER, new Spec("aden", "runner", "hmac", "Aden Runner pepper"),
            "platform.interview.db", new Spec("interview", "database", "password", "面试 PostgreSQL 口令"),
            "platform.aps.db", new Spec("aps", "database", "password", "排产 MySQL 口令"),
            "platform.ruoyi.redis", new Spec("ruoyi", "cache", "password", "Redis 口令"),
            "platform.ruoyi.druid-console", new Spec("ruoyi", "monitor", "password", "Druid 控制台口令"),
            "platform.ruoyi.mysql.slave", new Spec("ruoyi", "database", "password", "若依从库口令"));

    private ManagedSecretBootstrapCli() { }

    static void initialize(String alias) {
        Spec spec = ALLOWED.get(alias);
        if (spec == null) throw new IllegalArgumentException("不支持的自举密钥用途");
        Console console = System.console();
        if (console == null) throw new IllegalStateException("必须在可隐藏输入的交互式控制台运行初始化");
        String url = requireEnv("RUOYI_DB_URL");
        String user = requireEnv("RUOYI_DB_USERNAME");
        String password = requireEnv("RUOYI_DB_PASSWORD");
        char[] input = console.readPassword("请输入 %s 的首次密钥值（输入不会回显）：", alias);
        if (input == null || input.length < (JWT.equals(alias) ? 64 : 1)) {
            if (input != null) Arrays.fill(input, '\0');
            throw new IllegalArgumentException("首次密钥值长度无效");
        }
        try {
            String value = new String(input);
            if (ADEN_PEPPER.equals(alias)) validatePepper(value);
            if ("platform.ruoyi.druid-console".equals(alias) && value.length() < 16)
                throw new IllegalArgumentException("Druid 控制台口令至少需要 16 个字符");
            ManagedSecretCrypto.Envelope envelope = new ManagedSecretCrypto()
                    .encrypt("PLATFORM", alias, 1, value);
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                connection.setAutoCommit(false);
                try {
                    if (exists(connection, alias)) throw new IllegalStateException("密钥已经初始化，请通过平台密钥页面轮换");
                    LocalDateTime now = LocalDateTime.now();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO sys_managed_secret (secret_alias,secret_kind,project_code,provider_code,capability_code,auth_mode,display_name,status,active_version,row_version,created_by,created_at,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                        Object[] values = {alias, "PLATFORM", spec.project(), "",
                                spec.capability(), spec.mode(), spec.name(), "ACTIVE",
                                1, 1L, "bootstrap", now, "bootstrap", now};
                        for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO sys_managed_secret_version (secret_alias,version_no,provider_code,capability_code,auth_mode,key_id,nonce,ciphertext,created_by,created_at,change_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                        statement.setString(1, alias);
                        statement.setInt(2, 1);
                        statement.setString(3, "");
                        statement.setString(4, spec.capability());
                        statement.setString(5, spec.mode());
                        statement.setString(6, envelope.keyId());
                        statement.setBytes(7, envelope.nonce());
                        statement.setBytes(8, envelope.ciphertext());
                        statement.setString(9, "bootstrap");
                        statement.setObject(10, now);
                        statement.setString(11, "首次安全初始化");
                        statement.executeUpdate();
                    }
                    connection.commit();
                    console.printf("固定用途初始化完成：%s%n", alias);
                } catch (RuntimeException | SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("密钥初始化失败；数据库未更改或已回滚", exception);
        } finally {
            Arrays.fill(input, '\0');
        }
    }

    private static void validatePepper(String value) {
        try {
            var root = new tools.jackson.databind.ObjectMapper().readTree(value);
            String id = root.path("keyId").asText();
            String encoded = root.path("keyBase64").asText();
            if (id.isBlank() || id.length() > 64 || java.util.Base64.getDecoder().decode(encoded).length < 32)
                throw new IllegalArgumentException("Runner pepper 需要合法 keyId 和至少 32 字节 Base64 值");
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("Runner pepper 必须是合法 JSON 对象");
        }
    }

    private static boolean exists(Connection connection, String alias) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sys_managed_secret WHERE secret_alias=?")) {
            statement.setString(1, alias);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("自举环境缺少 " + name);
        return value;
    }
}
