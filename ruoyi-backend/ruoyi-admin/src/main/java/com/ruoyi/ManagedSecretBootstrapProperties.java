package com.ruoyi;

import com.ruoyi.system.secret.ManagedSecretCrypto;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 在 Spring 数据源/Redis 创建前，从主 MySQL 密钥表装配启动期平台凭据。 */
final class ManagedSecretBootstrapProperties {
    private ManagedSecretBootstrapProperties() { }

    static void install(ConfigurableEnvironment environment) {
        // 连接主 MySQL 的最低凭据是不可从同一库读取的自举例外。
        String url = System.getenv("RUOYI_DB_URL");
        String user = System.getenv("RUOYI_DB_USERNAME");
        String password = System.getenv("RUOYI_DB_PASSWORD");
        if (url == null || user == null || password == null) {
            throw new IllegalStateException("主 MySQL 自举连接未配置");
        }
        Map<String, Object> values = new HashMap<>();
        ManagedSecretCrypto crypto = new ManagedSecretCrypto();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            scalar(connection, crypto, values, "platform.ruoyi.redis", "spring.data.redis.password");
            String druidPassword = read(connection, crypto, "platform.ruoyi.druid-console");
            values.put("spring.datasource.druid.stat-view-servlet.login-password",
                    druidPassword == null ? "" : druidPassword);
            values.put("spring.datasource.druid.stat-view-servlet.enabled", druidPassword != null);
            requiredScalar(connection, crypto, values, "platform.interview.db", "interview.datasource.password",
                    environment.getProperty("interview.enabled", Boolean.class, true)
                            && hasText(environment.getProperty("interview.datasource.url")));
            requiredScalar(connection, crypto, values, "platform.aps.db", "aps.datasource.password",
                    environment.getProperty("aps.enabled", Boolean.class, false)
                            && environment.getProperty("aps.datasource.enabled", Boolean.class, false)
                            && hasText(environment.getProperty("aps.datasource.url")));
            scalar(connection, crypto, values, "platform.ruoyi.mysql.slave", "spring.datasource.druid.slave.password");
            pair(connection, crypto, values, "platform.interview.envelope.active",
                    "interview.voice-runtime.sensitive-envelope-key-id",
                    "interview.voice-runtime.sensitive-envelope-key-base64");
            pair(connection, crypto, values, "platform.interview.envelope.previous",
                    "interview.voice-runtime.previous-sensitive-envelope-key-id",
                    "interview.voice-runtime.previous-sensitive-envelope-key-base64");
            pair(connection, crypto, values, "platform.fashion.service.active",
                    "fashion.service-identity.active-key-id", "fashion.service-identity.active-key-base64");
            pair(connection, crypto, values, "platform.fashion.service.previous",
                    "fashion.service-identity.previous-key-id", "fashion.service-identity.previous-key-base64");
            pair(connection, crypto, values, "platform.fashion.contact.active",
                    "fashion.customer-contact.active-key-id", "fashion.customer-contact.active-key-base64");
            pair(connection, crypto, values, "platform.fashion.contact.previous",
                    "fashion.customer-contact.previous-key-id", "fashion.customer-contact.previous-key-base64");
        } catch (SQLException exception) {
            throw new IllegalStateException("主 MySQL 密钥存储不可用", exception);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("managed-mysql-secrets", values));
    }

    private static void scalar(Connection connection, ManagedSecretCrypto crypto,
                               Map<String, Object> values, String alias, String property) throws SQLException {
        String value = read(connection, crypto, alias);
        values.put(property, value == null ? "" : value);
    }

    private static void requiredScalar(Connection connection, ManagedSecretCrypto crypto,
                                       Map<String, Object> values, String alias, String property,
                                       boolean required) throws SQLException {
        String value = read(connection, crypto, alias);
        if (required && value == null) throw new IllegalStateException("启动必需的平台密钥未启用：" + alias);
        values.put(property, value == null ? "" : value);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static void pair(Connection connection, ManagedSecretCrypto crypto,
                             Map<String, Object> values, String alias, String idProperty,
                             String keyProperty) throws SQLException {
        String value = read(connection, crypto, alias);
        if (value == null) {
            values.put(idProperty, "");
            values.put(keyProperty, "");
            return;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(value);
            String keyId = root.path("keyId").asText();
            String keyBase64 = root.path("keyBase64").asText();
            if (keyId.isBlank() || keyBase64.isBlank()) throw new IllegalStateException("平台密钥组不完整：" + alias);
            values.put(idProperty, keyId);
            values.put(keyProperty, keyBase64);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("平台密钥组格式无效：" + alias);
        }
    }

    static String read(Connection connection, ManagedSecretCrypto crypto, String alias) throws SQLException {
        String sql = "SELECT s.secret_kind,s.status,s.active_version,v.key_id,v.nonce,v.ciphertext "
                + "FROM sys_managed_secret s JOIN sys_managed_secret_version v "
                + "ON v.secret_alias=s.secret_alias AND v.version_no=s.active_version "
                + "WHERE s.secret_alias=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alias);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                if (!"PLATFORM".equals(result.getString("secret_kind"))
                        || !"ACTIVE".equals(result.getString("status"))) return null;
                int version = result.getInt("active_version");
                return crypto.decrypt("PLATFORM", alias, version,
                        new ManagedSecretCrypto.Envelope(result.getString("key_id"),
                                result.getBytes("nonce"), result.getBytes("ciphertext")));
            }
        }
    }
}
