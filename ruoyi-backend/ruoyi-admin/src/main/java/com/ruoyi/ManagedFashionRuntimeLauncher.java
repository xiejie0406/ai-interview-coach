package com.ruoyi;

import com.ruoyi.system.secret.ManagedSecretCrypto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fashion Runtime 子进程的 HMAC 值只从平台密钥读取，经一次性匿名管道交付。 */
final class ManagedFashionRuntimeLauncher {
    private ManagedFashionRuntimeLauncher() { }

    static int launch(String[] command) {
        if (command.length == 0) throw new IllegalArgumentException("需要提供 Python Runtime 启动命令");
        String url = requireEnv("RUOYI_DB_URL");
        String user = requireEnv("RUOYI_DB_USERNAME");
        String password = requireEnv("RUOYI_DB_PASSWORD");
        ObjectMapper json = new ObjectMapper();
        Map<String, Object> payload = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            ManagedSecretCrypto crypto = new ManagedSecretCrypto();
            payload.put("active", pair(json, ManagedSecretBootstrapProperties.read(
                    connection, crypto, "platform.fashion.service.active"), true));
            payload.put("previous", pair(json, ManagedSecretBootstrapProperties.read(
                    connection, crypto, "platform.fashion.service.previous"), false));
        } catch (SQLException exception) {
            throw new IllegalStateException("Fashion 服务认证密钥无法从 MySQL 读取", exception);
        }
        ProcessBuilder builder = new ProcessBuilder(Arrays.asList(command));
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Map<String, String> childEnv = builder.environment();
        childEnv.keySet().removeIf(name -> name.equals("MYSQL_PWD") || name.startsWith("RUOYI_DB_")
                || name.startsWith("RUOYI_MANAGED_SECRET_")
                || name.startsWith("FASHION_SERVICE_")
                || name.startsWith("FASHION_AI_AUTH_ACTIVE_")
                || name.startsWith("FASHION_AI_AUTH_PREVIOUS_"));
        childEnv.put("FASHION_AI_MANAGED_SECRET_STDIN", "1");
        try {
            Process child = builder.start();
            try (var stdin = child.getOutputStream()) {
                stdin.write(json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
                stdin.write('\n');
            }
            return child.waitFor();
        } catch (IOException exception) {
            throw new IllegalStateException("Fashion Runtime 启动或密钥交付失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fashion Runtime 启动被中断", exception);
        }
    }

    private static Map<String, String> pair(ObjectMapper json, String value, boolean required) {
        if (value == null) {
            if (required) throw new IllegalStateException("Fashion 服务认证 active 密钥未启用");
            return null;
        }
        try {
            JsonNode root = json.readTree(value);
            String id = root.path("keyId").asText();
            String base64 = root.path("keyBase64").asText();
            if (id.isBlank() || base64.isBlank()) throw new IllegalStateException("Fashion 服务认证密钥组不完整");
            return Map.of("keyId", id, "keyBase64", base64);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Fashion 服务认证密钥组格式无效");
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("自举环境缺少 " + name);
        return value;
    }
}
