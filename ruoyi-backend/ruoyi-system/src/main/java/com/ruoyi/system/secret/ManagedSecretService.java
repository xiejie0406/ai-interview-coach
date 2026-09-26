package com.ruoyi.system.secret;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台密钥与 AI 密钥共用的 MySQL 存储；读取明文仅供服务端固定用途调用。 */
@Service
public class ManagedSecretService {
    public record Metadata(String alias, String kind, String projectCode, String providerCode,
                           String capabilityCode, String authMode, String displayName,
                           String status, Integer activeVersion, long rowVersion,
                           LocalDateTime updatedAt) { }

    public record Audit(long id, String alias, String action, Integer version,
                        String operator, String reason, LocalDateTime occurredAt) { }
    public record VersionInfo(int version, String provider, String capability, String authMode,
                              String createdBy, LocalDateTime createdAt,
                              String reason, boolean active) { }
    /** 单次 SQL 读取同一版本的元数据和密文，防止轮换时模式与值错配。 */
    public record Resolved(Metadata metadata, String value) { }

    private static final RowMapper<Metadata> METADATA = (rs, row) -> new Metadata(
            rs.getString("secret_alias"), rs.getString("secret_kind"),
            rs.getString("project_code"), rs.getString("provider_code"),
            rs.getString("capability_code"), rs.getString("auth_mode"),
            rs.getString("display_name"), rs.getString("status"),
            (Integer) rs.getObject("active_version"), rs.getLong("row_version"),
            rs.getTimestamp("updated_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final ManagedSecretCrypto crypto;

    public ManagedSecretService(@Qualifier("dynamicDataSource") DataSource dataSource,
                                ManagedSecretCrypto crypto) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.crypto = crypto;
    }

    public List<Metadata> list(String kind, String project) {
        requireKind(kind);
        if (project == null || project.isBlank()) {
            return jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_kind=? ORDER BY project_code, secret_alias",
                    METADATA, kind);
        }
        return jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_kind=? AND project_code=? ORDER BY secret_alias",
                METADATA, kind, project);
    }

    public Metadata metadata(String kind, String alias) {
        requireKind(kind);
        List<Metadata> rows = jdbc.query(
                "SELECT * FROM sys_managed_secret WHERE secret_kind=? AND secret_alias=?", METADATA, kind, alias);
        if (rows.isEmpty()) throw new IllegalArgumentException("密钥用途不存在");
        return rows.get(0);
    }

    public List<VersionInfo> versions(String kind, String alias) {
        Metadata current = metadata(kind, alias);
        return jdbc.query("SELECT version_no,provider_code,capability_code,auth_mode,created_by,created_at,change_reason "
                        + "FROM sys_managed_secret_version WHERE secret_alias=? ORDER BY version_no DESC LIMIT 100",
                (rs, row) -> new VersionInfo(rs.getInt("version_no"), rs.getString("provider_code"),
                        rs.getString("capability_code"), rs.getString("auth_mode"), rs.getString("created_by"),
                        rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("change_reason"),
                        "ACTIVE".equals(current.status()) && current.activeVersion() != null
                                && rs.getInt("version_no") == current.activeVersion()), alias);
    }

    /** 入库后旧版本不可修改；写入与当前版本指针在同一事务中提交。 */
    @Transactional("managedSecretTransactionManager")
    public Metadata put(String kind, String alias, String project, String provider, String capability,
                        String authMode, String name, String value, String reason,
                        Long expectedRowVersion, String operator) {
        if (isReservedPrevious(alias))
            throw new IllegalArgumentException("上一版用途只能通过保留上一版操作写入");
        return write(kind, alias, project, provider, capability, authMode, name, value,
                reason, expectedRowVersion, operator, "ROTATE");
    }

    private Metadata write(String kind, String alias, String project, String provider, String capability,
                           String authMode, String name, String value, String reason,
                           Long expectedRowVersion, String operator, String action) {
        requireKind(kind);
        requireCode(alias, 128);
        if (("PLATFORM".equals(kind) && !alias.startsWith("platform."))
                || ("AI".equals(kind) && !alias.startsWith("ai."))) {
            throw new IllegalArgumentException("密钥用途类别与别名前缀不匹配");
        }
        requireCode(project, 64);
        requireText(name, 128);
        requireText(reason, 500);
        requireText(value, 16_384);
        if (value.length() >= 8 && reason.contains(value)) throw new IllegalArgumentException("变更原因不得包含密钥值");
        String safeProvider = optionalCode(provider, 64);
        String safeCapability = optionalCode(capability, 64);
        String safeMode = optionalCode(authMode, 32);
        if ("AI".equals(kind) && safeProvider.isBlank()) {
            throw new IllegalArgumentException("AI 密钥必须指定供应商");
        }
        validateKnownCredential(kind, alias, project, safeProvider, safeCapability, safeMode, value);
        List<Metadata> existing = jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_alias=? FOR UPDATE",
                METADATA, alias);
        int version;
        LocalDateTime now = LocalDateTime.now();
        if (existing.isEmpty()) {
            if (expectedRowVersion != null) throw new IllegalStateException("密钥用途已变化，请刷新后重试");
            jdbc.update("INSERT INTO sys_managed_secret (secret_alias,secret_kind,project_code,provider_code,capability_code,auth_mode,display_name,status,active_version,row_version,created_by,created_at,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    alias, kind, project, safeProvider, safeCapability, safeMode, name, "ACTIVE", 1,
                    1L, operator, now, operator, now);
            version = 1;
        } else {
            Metadata old = existing.get(0);
            if (!kind.equals(old.kind()) || !project.equals(old.projectCode())) {
                throw new IllegalArgumentException("密钥用途归属不可修改");
            }
            if ("AI".equals(kind) && (!safeProvider.equals(old.providerCode())
                    || !safeCapability.equals(old.capabilityCode()))) {
                throw new IllegalArgumentException("AI 密钥用途的供应商和能力不可修改，请新建用途");
            }
            if (expectedRowVersion == null || old.rowVersion() != expectedRowVersion) {
                throw new IllegalStateException("密钥版本已变化，请刷新后重试");
            }
            if (requiresPrevious(alias)) {
                if (old.activeVersion() == null) throw new IllegalStateException("当前密钥版本缺失");
                String oldValue = readVersion(kind, alias, old.activeVersion());
                if (!oldValue.equals(value)) {
                    String previous = alias.equals("platform.ruoyi.jwt") ? "platform.ruoyi.jwt.previous"
                            : alias.equals("platform.aden.runner-pepper") ? "platform.aden.runner-pepper.previous"
                            : alias.replace(".active", ".previous");
                    List<Metadata> staged = jdbc.query(
                            "SELECT * FROM sys_managed_secret WHERE secret_kind='PLATFORM' AND secret_alias=? FOR UPDATE",
                            METADATA, previous);
                    if (staged.isEmpty() || !"ACTIVE".equals(staged.get(0).status())
                            || !oldValue.equals(resolve("PLATFORM", previous).value())) {
                        throw new IllegalStateException("轮换前必须先保留当前版本到 previous 用途");
                    }
                }
            }
            version = old.activeVersion() == null ? 1 : old.activeVersion() + 1;
            jdbc.update("UPDATE sys_managed_secret SET provider_code=?,capability_code=?,auth_mode=?,display_name=?,status='ACTIVE',active_version=?,row_version=row_version+1,updated_by=?,updated_at=? WHERE secret_alias=?",
                    safeProvider, safeCapability, safeMode, name, version, operator, now, alias);
        }
        ManagedSecretCrypto.Envelope encrypted = crypto.encrypt(kind, alias, version, value);
        jdbc.update("INSERT INTO sys_managed_secret_version (secret_alias,version_no,provider_code,capability_code,auth_mode,key_id,nonce,ciphertext,created_by,created_at,change_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                alias, version, safeProvider, safeCapability, safeMode, encrypted.keyId(), encrypted.nonce(),
                encrypted.ciphertext(), operator, now, reason);
        audit(alias, kind, action, version, operator, reason, now);
        return metadata(kind, alias);
    }

    @Transactional("managedSecretTransactionManager")
    public Metadata disable(String kind, String alias, String reason,
                            long expectedRowVersion, String operator) {
        requireKind(kind);
        if ("PLATFORM".equals(kind) && "platform.ruoyi.jwt".equals(alias))
            throw new IllegalArgumentException("若依 JWT 当前签名密钥不可停用，请通过轮换和 previous 用途管理");
        requireText(reason, 500);
        List<Metadata> locked = jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_kind=? AND secret_alias=? FOR UPDATE",
                METADATA, kind, alias);
        if (locked.isEmpty()) throw new IllegalArgumentException("密钥用途不存在");
        Metadata old = locked.get(0);
        if (old.rowVersion() != expectedRowVersion) throw new IllegalStateException("密钥版本已变化，请刷新后重试");
        jdbc.update("UPDATE sys_managed_secret SET status='DISABLED',row_version=row_version+1,updated_by=?,updated_at=? WHERE secret_kind=? AND secret_alias=?",
                operator, LocalDateTime.now(), kind, alias);
        audit(alias, kind, "DISABLE", old.activeVersion(), operator, reason, LocalDateTime.now());
        return metadata(kind, alias);
    }

    /** 轮换前复制当前生效值到固定 previous 用途；API 不回传明文。 */
    @Transactional("managedSecretTransactionManager")
    public Metadata stagePrevious(String alias, long expectedRowVersion, String operator) {
        String previous = switch (alias) {
            case "platform.ruoyi.jwt" -> "platform.ruoyi.jwt.previous";
            case "platform.fashion.service.active" -> "platform.fashion.service.previous";
            case "platform.fashion.contact.active" -> "platform.fashion.contact.previous";
            case "platform.interview.envelope.active" -> "platform.interview.envelope.previous";
            case "platform.aden.runner-pepper" -> "platform.aden.runner-pepper.previous";
            default -> throw new IllegalArgumentException("该用途不支持 previous 轮换");
        };
        List<Metadata> locked = jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_kind='PLATFORM' AND secret_alias=? FOR UPDATE",
                METADATA, alias);
        if (locked.isEmpty() || locked.get(0).rowVersion() != expectedRowVersion)
            throw new IllegalStateException("密钥版本已变化，请刷新后重试");
        Metadata source = locked.get(0);
        Resolved current = resolve("PLATFORM", alias);
        List<Metadata> prior = jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_alias=?",
                METADATA, previous);
        if (!prior.isEmpty() && "ACTIVE".equals(prior.get(0).status())) {
            if (current.value().equals(resolve("PLATFORM", previous).value())) return prior.get(0);
            throw new IllegalStateException("上一版仍在生效；确认旧会话或历史密文已处理并停用后才能再次保留");
        }
        Long previousVersion = prior.isEmpty() ? null : prior.get(0).rowVersion();
        return write("PLATFORM", previous, source.projectCode(), source.providerCode(),
                source.capabilityCode(), source.authMode(), source.displayName().substring(0,
                        Math.min(source.displayName().length(), 120)) + "（上一版）",
                current.value(), "轮换前保留当前生效值", previousVersion, operator, "STAGE_PREVIOUS");
    }

    /** 历史版本只作为新版本的来源，不倒拨生效指针。 */
    @Transactional("managedSecretTransactionManager")
    public Metadata restoreAsNewVersion(String kind, String alias, int sourceVersion,
                                        long expectedRowVersion, String reason, String operator) {
        requireKind(kind);
        if (isReservedPrevious(alias))
            throw new IllegalArgumentException("上一版用途不可直接恢复，请从当前用途重新保留");
        requireText(reason, 300);
        if (sourceVersion < 1) throw new IllegalArgumentException("历史版本号无效");
        List<Metadata> locked = jdbc.query("SELECT * FROM sys_managed_secret WHERE secret_kind=? AND secret_alias=? FOR UPDATE",
                METADATA, kind, alias);
        if (locked.isEmpty() || locked.get(0).rowVersion() != expectedRowVersion)
            throw new IllegalStateException("密钥版本已变化，请刷新后重试");
        Metadata current = locked.get(0);
        if ("ACTIVE".equals(current.status()) && current.activeVersion() != null
                && current.activeVersion() == sourceVersion)
            throw new IllegalArgumentException("该版本已是当前生效版本");
        record Historical(String provider, String capability, String mode, ManagedSecretCrypto.Envelope envelope) { }
        List<Historical> source = jdbc.query(
                "SELECT provider_code,capability_code,auth_mode,key_id,nonce,ciphertext FROM sys_managed_secret_version WHERE secret_alias=? AND version_no=?",
                (rs, row) -> new Historical(rs.getString("provider_code"), rs.getString("capability_code"),
                        rs.getString("auth_mode"), new ManagedSecretCrypto.Envelope(rs.getString("key_id"),
                        rs.getBytes("nonce"), rs.getBytes("ciphertext"))), alias, sourceVersion);
        if (source.size() != 1) throw new IllegalArgumentException("历史版本不存在");
        Historical historical = source.get(0);
        String value = crypto.decrypt(kind, alias, sourceVersion, historical.envelope());
        return write(kind, alias, current.projectCode(), historical.provider(),
                historical.capability(), historical.mode(), current.displayName(), value,
                "恢复 V" + sourceVersion + "：" + reason, expectedRowVersion, operator, "RESTORE");
    }

    /** 不得在 Controller 暴露此方法；调用方必须使用固定的服务端 alias。 */
    public String require(String kind, String alias) {
        return resolve(kind, alias).value();
    }

    public Resolved resolve(String kind, String alias) {
        requireKind(kind);
        List<Resolved> rows = jdbc.query("SELECT s.*,v.key_id,v.nonce,v.ciphertext "
                        + "FROM sys_managed_secret s JOIN sys_managed_secret_version v "
                        + "ON v.secret_alias=s.secret_alias AND v.version_no=s.active_version "
                        + "WHERE s.secret_kind=? AND s.secret_alias=? AND s.status='ACTIVE'",
                (rs, row) -> {
                    Metadata metadata = METADATA.mapRow(rs, row);
                    ManagedSecretCrypto.Envelope envelope = new ManagedSecretCrypto.Envelope(
                            rs.getString("key_id"), rs.getBytes("nonce"), rs.getBytes("ciphertext"));
                    return new Resolved(metadata, crypto.decrypt(kind, alias, metadata.activeVersion(), envelope));
                }, kind, alias);
        if (rows.size() != 1) throw new IllegalStateException("密钥用途未启用或版本不存在");
        return rows.get(0);
    }

    public List<Audit> audit(String kind, String alias) {
        requireKind(kind);
        metadata(kind, alias);
        return jdbc.query("SELECT * FROM sys_managed_secret_audit WHERE secret_kind=? AND secret_alias=? ORDER BY audit_id DESC LIMIT 200",
                (rs, row) -> new Audit(rs.getLong("audit_id"), rs.getString("secret_alias"),
                        rs.getString("action_code"), (Integer) rs.getObject("version_no"),
                        rs.getString("operator_name"), rs.getString("change_reason"),
                        rs.getTimestamp("occurred_at").toLocalDateTime()), kind, alias);
    }

    private void audit(String alias, String kind, String action, Integer version,
                       String operator, String reason, LocalDateTime at) {
        jdbc.update("INSERT INTO sys_managed_secret_audit (secret_alias,secret_kind,action_code,version_no,operator_name,change_reason,occurred_at) VALUES (?,?,?,?,?,?,?)",
                alias, kind, action, version, operator, reason, at);
    }

    private static void requireKind(String kind) {
        if (!"PLATFORM".equals(kind) && !"AI".equals(kind)) throw new IllegalArgumentException("密钥类别无效");
    }

    private static boolean isReservedPrevious(String alias) {
        return alias != null && (alias.equals("platform.ruoyi.jwt.previous")
                || alias.equals("platform.fashion.service.previous")
                || alias.equals("platform.fashion.contact.previous")
                || alias.equals("platform.interview.envelope.previous")
                || alias.equals("platform.aden.runner-pepper.previous"));
    }

    private static boolean requiresPrevious(String alias) {
        return alias != null && (alias.equals("platform.ruoyi.jwt")
                || alias.equals("platform.fashion.service.active")
                || alias.equals("platform.fashion.contact.active")
                || alias.equals("platform.interview.envelope.active")
                || alias.equals("platform.aden.runner-pepper"));
    }

    private String readVersion(String kind, String alias, int version) {
        List<ManagedSecretCrypto.Envelope> rows = jdbc.query(
                "SELECT key_id,nonce,ciphertext FROM sys_managed_secret_version WHERE secret_alias=? AND version_no=?",
                (rs, row) -> new ManagedSecretCrypto.Envelope(rs.getString("key_id"),
                        rs.getBytes("nonce"), rs.getBytes("ciphertext")), alias, version);
        if (rows.size() != 1) throw new IllegalStateException("当前密钥版本不存在");
        return crypto.decrypt(kind, alias, version, rows.get(0));
    }

    static void validateKnownCredential(String kind, String alias, String project,
                                        String provider, String capability, String mode, String value) {
        if ("PLATFORM".equals(kind)) {
            validatePlatformCredential(alias, value);
            return;
        }
        if ("ai.interview.deepseek".equals(alias)) {
            if (!"interview".equals(project) || !"deepseek".equals(provider)
                    || !"chat".equals(capability) || !"api-key".equals(mode))
                throw new IllegalArgumentException("DeepSeek 用途必须为 interview/deepseek/chat/api-key");
            return;
        }
        if (!"ai.interview.volcengine.speech".equals(alias)) return;
        if (!"interview".equals(project) || !"volcengine".equals(provider)
                || !"speech".equals(capability))
            throw new IllegalArgumentException("火山语音用途必须为 interview/volcengine/speech");
        if ("api-key".equals(mode)) {
            if (value.trim().startsWith("{")) throw new IllegalArgumentException("API Key 模式不得混入 Token 组");
            return;
        }
        if (!"access-token".equals(mode)) throw new IllegalArgumentException("火山语音鉴权模式无效");
        try {
            var node = new tools.jackson.databind.ObjectMapper().readTree(value);
            if (!node.isObject() || node.path("appId").asText().isBlank()
                    || node.path("accessToken").asText().isBlank()
                    || node.has("apiKey")) {
                throw new IllegalArgumentException("火山 App ID 与 Access Token 必须完整且不得混用 API Key");
            }
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("火山凭据组 JSON 无效");
        }
    }

    private static void validatePlatformCredential(String alias, String value) {
        if (alias.startsWith("platform.ruoyi.jwt") && value.length() < 64)
            throw new IllegalArgumentException("JWT 签名密钥至少需要 64 个字符");
        if ("platform.ruoyi.druid-console".equals(alias) && value.length() < 16)
            throw new IllegalArgumentException("Druid 控制台口令至少需要 16 个字符");
        boolean pair = alias.startsWith("platform.fashion.service.")
                || alias.startsWith("platform.fashion.contact.")
                || alias.startsWith("platform.interview.envelope.")
                || alias.startsWith("platform.aden.runner-pepper");
        if (!pair) return;
        try {
            var root = new tools.jackson.databind.ObjectMapper().readTree(value);
            String id = root.path("keyId").asText();
            String encoded = root.path("keyBase64").asText();
            if (!root.isObject() || id.isBlank() || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}"))
                throw new IllegalArgumentException("平台密钥组 keyId 无效");
            int length = Base64.getDecoder().decode(encoded).length;
            boolean exact = alias.startsWith("platform.fashion.contact.")
                    || alias.startsWith("platform.interview.envelope.");
            if (exact ? length != 32 : length < 32)
                throw new IllegalArgumentException("平台密钥组长度无效");
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("平台密钥组须包含合法 keyId 和 Base64 密钥");
        }
    }

    private static void requireCode(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new IllegalArgumentException("标识格式无效");
        }
    }

    private static String optionalCode(String value, int max) {
        if (value == null || value.isBlank()) return "";
        requireCode(value, max);
        return value;
    }

    private static void requireText(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("输入内容长度无效");
        }
    }
}
