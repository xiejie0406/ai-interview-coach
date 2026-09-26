package com.ruoyi.system.airole;

import com.ruoyi.system.secret.ManagedSecretService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** AI 助手人设与模型配置版本；和若依用户角色完全分离。 */
@Service
public class AiRoleService {
    public record Role(long id, String code, String project, String name, String purpose,
                       String status, Integer activeVersion, long rowVersion) { }
    public record Version(long roleId, int version, String persona, String systemPrompt,
                          String provider, String model, String aiSecretAlias,
                          String modelConfigJson, String contentHash, String status,
                          LocalDateTime createdAt) { }

    private static final RowMapper<Role> ROLE_ROW = (rs, row) -> new Role(
            rs.getLong("role_id"), rs.getString("role_code"), rs.getString("project_code"),
            rs.getString("display_name"), rs.getString("purpose"), rs.getString("status"),
            (Integer) rs.getObject("active_version"), rs.getLong("row_version"));
    private static final RowMapper<Version> VERSION_ROW = (rs, row) -> new Version(
            rs.getLong("role_id"), rs.getInt("version_no"), rs.getString("persona"),
            rs.getString("system_prompt"), rs.getString("provider_code"),
            rs.getString("model_name"), rs.getString("ai_secret_alias"),
            rs.getString("model_config_json"), rs.getString("content_hash"),
            rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final ManagedSecretService secrets;
    private final ObjectMapper json;

    public AiRoleService(@Qualifier("dynamicDataSource") DataSource dataSource,
                         ManagedSecretService secrets, ObjectMapper json) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.secrets = secrets;
        this.json = json;
    }

    public List<Role> list(String project) {
        if (project == null || project.isBlank()) {
            return jdbc.query("SELECT * FROM sys_ai_role ORDER BY project_code,role_code", ROLE_ROW);
        }
        return jdbc.query("SELECT * FROM sys_ai_role WHERE project_code=? ORDER BY role_code",
                ROLE_ROW, project);
    }

    public Role get(long id) {
        List<Role> rows = jdbc.query("SELECT * FROM sys_ai_role WHERE role_id=?", ROLE_ROW, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("AI 角色不存在");
        return rows.get(0);
    }

    public List<Version> versions(long id) {
        get(id);
        return jdbc.query("SELECT * FROM sys_ai_role_version WHERE role_id=? ORDER BY version_no DESC",
                VERSION_ROW, id);
    }

    @Transactional("managedSecretTransactionManager")
    public Role create(String code, String project, String name, String purpose, String operator) {
        code(code, 64);
        code(project, 64);
        text(name, 100);
        text(purpose, 500);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO sys_ai_role (role_code,project_code,display_name,purpose,status,active_version,row_version,created_by,created_at,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                code, project, name, purpose, "DRAFT", null, 0L, operator, now, operator, now);
        Long id = jdbc.queryForObject("SELECT role_id FROM sys_ai_role WHERE project_code=? AND role_code=?",
                Long.class, project, code);
        if (id == null) throw new IllegalStateException("AI 角色创建失败");
        return get(id);
    }

    @Transactional("managedSecretTransactionManager")
    public Version createVersion(long id, String persona, String prompt, String provider,
                                 String model, String secretAlias, String configJson,
                                 String operator) {
        lock(id);
        text(persona, 5000);
        text(prompt, 20000);
        code(provider, 64);
        text(model, 128);
        code(secretAlias, 128);
        text(configJson, 200000);
        try {
            var config = json.readTree(configJson);
            if (!config.isObject()) throw new IllegalArgumentException("模型配置必须是 JSON 对象");
            if (containsSecretField(config)) throw new IllegalArgumentException("模型配置不得包含密钥字段");
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("模型配置 JSON 无效");
        }
        int next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM sys_ai_role_version WHERE role_id=?",
                Integer.class, id);
        String hash = hash(persona + "\n" + prompt + "\n" + provider + "\n" + model + "\n"
                + secretAlias + "\n" + configJson);
        jdbc.update("INSERT INTO sys_ai_role_version (role_id,version_no,persona,system_prompt,provider_code,model_name,ai_secret_alias,model_config_json,content_hash,status,created_by,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, next, persona, prompt, provider, model, secretAlias, configJson,
                hash, "DRAFT", operator, LocalDateTime.now());
        return version(id, next);
    }

    @Transactional("managedSecretTransactionManager")
    public Role publish(long id, int version, long expectedRowVersion, String operator) {
        Role role = lock(id);
        if (role.rowVersion() != expectedRowVersion) throw new IllegalStateException("角色版本已变化，请刷新后重试");
        Version target = version(id, version);
        if (!"DRAFT".equals(target.status())) throw new IllegalArgumentException("只能发布草稿版本");
        ManagedSecretService.Metadata secret = secrets.metadata("AI", target.aiSecretAlias());
        if (!"ACTIVE".equals(secret.status()) || !"chat".equals(secret.capabilityCode())
                || !target.provider().equals(secret.providerCode())
                || !role.project().equals(secret.projectCode())) {
            throw new IllegalArgumentException("AI 密钥未启用、项目或供应商不匹配");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE sys_ai_role_version SET status='RETIRED' WHERE role_id=? AND status='PUBLISHED'", id);
        jdbc.update("UPDATE sys_ai_role_version SET status='PUBLISHED',published_by=?,published_at=? WHERE role_id=? AND version_no=?",
                operator, now, id, version);
        jdbc.update("UPDATE sys_ai_role SET status='ACTIVE',active_version=?,row_version=row_version+1,updated_by=?,updated_at=? WHERE role_id=?",
                version, operator, now, id);
        return get(id);
    }

    private Role lock(long id) {
        List<Role> rows = jdbc.query("SELECT * FROM sys_ai_role WHERE role_id=? FOR UPDATE", ROLE_ROW, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("AI 角色不存在");
        return rows.get(0);
    }

    private Version version(long id, int version) {
        List<Version> rows = jdbc.query("SELECT * FROM sys_ai_role_version WHERE role_id=? AND version_no=?",
                VERSION_ROW, id, version);
        if (rows.isEmpty()) throw new IllegalArgumentException("AI 角色版本不存在");
        return rows.get(0);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static boolean containsSecretField(tools.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            for (var field : node.properties()) {
                if (field.getKey().matches("(?i).*(api.?key|secret|token|password|credential|private.?key).*")
                        || containsSecretField(field.getValue())) return true;
            }
        } else if (node.isArray()) {
            for (var item : node) if (containsSecretField(item)) return true;
        }
        return false;
    }

    private static void code(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new IllegalArgumentException("标识格式无效");
        }
    }

    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("输入内容长度无效");
        }
    }
}
