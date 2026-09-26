package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.platform.ServerSideDigest;
import com.ruoyi.interview.domain.catalog.QuestionCategory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 题库批量导入的纯 Java、零写入校验器。
 *
 * <p>该类只验证一个已经生成的清单及其导入后的只读回读结果，不创建题目、版本、Rubric，
 * 也不推进发布指针。生产导入器应在写入事务前调用 {@link #validate(ImportManifest)}，
 * 并在事务提交后用同一个清单再次调用它完成回读校验；校验失败时由调用方停止导入或回滚。</p>
 *
 * <p>清单指纹使用 {@link #fingerprintOf(ImportManifest)} 计算。指纹绑定格式版本、目标租户、
 * batch、题目数量、模块集合及每条题目的不可变字段/答案/来源字段，但不绑定传输层的
 * {@code idempotencyKey}，这样同一内容可以在重试时复用不同的 HTTP 幂等键。算法使用
 * {@link ServerSideDigest#sha256(String...)} 的长度前缀编码，避免分隔符歧义。</p>
 *
 * <p>本类没有 Spring、JDBC、网络或文件依赖，便于在导入命令行、事务服务和单元测试中复用。</p>
 */
public final class CatalogImportManifestValidator {

    /** V2 导入契约要求的固定题目总数。 */
    public static final int EXPECTED_QUESTION_COUNT = 606;

    /** V2 导入契约要求的固定模块数量。 */
    public static final int EXPECTED_MODULE_COUNT = 12;

    /** 指纹算法版本；改变字段顺序或编码时必须递增。 */
    public static final String FINGERPRINT_VERSION = "catalog-import-manifest:v1";

    private static final int MAX_BATCH_ID_LENGTH = 128;
    private static final int MIN_IDEMPOTENCY_KEY_LENGTH = 16;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_STABLE_KEY_LENGTH = 160;
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_ANSWER_ITEMS = 30;
    private static final int MAX_ANSWER_ITEM_LENGTH = 1_000;
    private static final int MAX_TITLE_LENGTH = 500;
    private static final int MAX_STEM_LENGTH = 8_000;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("JUNIOR", "MID", "SENIOR");
    private static final Set<String> ALLOWED_TARGET_ROLES = Set.of("JAVA_BACKEND", "AI_APPLICATION", "AGENT_ENGINEER");
    private static final List<String> FIXED_MODULES = List.copyOf(
            QuestionCategory.FIXED_MODULES.stream().sorted().toList());
    private static final Set<String> FIXED_MODULE_SET = Set.copyOf(FIXED_MODULES);

    private final ReadbackPort readbackPort;
    private final String configuredTenantId;

    /**
     * 创建需要数据库回读的校验器。回读端口必须是只读实现；校验器不会调用任何写方法。
     */
    public CatalogImportManifestValidator(ReadbackPort readbackPort) {
        this(readbackPort, null);
    }

    /**
     * 创建校验器，并为不在清单内的历史调用方提供目标租户配置。
     * 新的 canonical 清单应直接填写 {@link ImportManifest#catalogTenantId()}。
     */
    public CatalogImportManifestValidator(ReadbackPort readbackPort, String configuredTenantId) {
        this.readbackPort = Objects.requireNonNull(readbackPort, "readbackPort");
        this.configuredTenantId = configuredTenantId;
    }

    /**
     * 校验清单及导入后的数据库回读。结构校验失败时不会调用回读端口。
     */
    public ValidationResult validate(ImportManifest manifest) {
        List<Violation> violations = new ArrayList<>();
        if (manifest == null) {
            violations.add(Violation.of("MANIFEST_REQUIRED", "manifest", "import manifest is required"));
            return result(violations, null, 0, 0, false);
        }

        String computedFingerprint = fingerprintOf(manifest);
        validateManifestShape(manifest, violations, true);

        ReadbackCheck readbackCheck = ReadbackCheck.notAttempted();

        if (violations.isEmpty()) {
            String tenantId = resolvedTenantId(manifest);
            if (isBlank(tenantId)) {
                violations.add(Violation.of("TENANT_REQUIRED", "catalogTenantId",
                        "catalog tenant is required before database readback"));
            } else {
                readbackCheck = validateReadback(manifest, tenantId, violations);
            }
        }
        int manifestCount = manifest.questions() == null ? 0 : manifest.questions().size();
        return result(violations, computedFingerprint, manifestCount, readbackCheck.databaseCount(),
                readbackCheck.attempted());
    }

    /**
     * 只校验清单本身，不执行数据库读取。适用于导入事务开始前的预检。
     */
    public static ValidationResult validateStructure(ImportManifest manifest) {
        CatalogImportManifestValidator validator = new CatalogImportManifestValidator(
                (tenantId, stableKeys) -> Readback.empty());
        // 结构预检不应因为没有回读租户而失败；直接复用内部方法避免伪造成功的数据库结果。
        if (manifest == null) {
            return validator.validate(null);
        }
        List<Violation> violations = new ArrayList<>();
        String fingerprint = fingerprintOf(manifest);
        validator.validateManifestShape(manifest, violations, false);
        int manifestCount = manifest.questions() == null ? 0 : manifest.questions().size();
        return validator.result(violations, fingerprint, manifestCount, 0, false);
    }

    /** 返回清单是否通过结构和回读校验。 */
    public boolean isValid(ImportManifest manifest) {
        return validate(manifest).valid();
    }

    /**
     * 便于离线/单元场景直接提供一份只读回读快照；该重载同样不会执行任何写操作。
     */
    public static ValidationResult validate(ImportManifest manifest, Readback readback) {
        if (manifest == null) {
            return validateStructure(null);
        }
        CatalogImportManifestValidator validator = new CatalogImportManifestValidator(
                (ignoredTenant, ignoredStableKeys) -> readback, manifest.catalogTenantId());
        return validator.validate(manifest);
    }

    /**
     * 计算清单指纹。调用方可先构造 fingerprint 为空的清单，再把返回值写入清单。
     */
    public static String fingerprintOf(ImportManifest manifest) {
        if (manifest == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(FINGERPRINT_VERSION);
        parts.add(manifest.catalogTenantId());
        parts.add(manifest.batchId());
        parts.add(Integer.toString(manifest.declaredQuestionCount()));

        if (manifest.modules() == null) {
            parts.add(null);
        } else {
            List<String> modules = new ArrayList<>(manifest.modules());
            modules.sort(Comparator.nullsFirst(Comparator.<String>naturalOrder()));
            parts.add(Integer.toString(modules.size()));
            modules.forEach(parts::add);
        }

        if (manifest.questions() == null) {
            parts.add(null);
        } else {
            List<QuestionEntry> entries = new ArrayList<>(manifest.questions());
            entries.sort((left, right) -> {
                String leftKey = left == null ? null : left.stableKey();
                String rightKey = right == null ? null : right.stableKey();
                return Comparator.nullsFirst(Comparator.<String>naturalOrder()).compare(leftKey, rightKey);
            });
            parts.add(Integer.toString(entries.size()));
            for (QuestionEntry entry : entries) {
                appendEntry(parts, entry);
            }
        }
        return ServerSideDigest.sha256(parts.toArray(new String[0]));
    }

    private static void appendEntry(List<String> parts, QuestionEntry entry) {
        if (entry == null) {
            parts.add(null);
            return;
        }
        parts.add(entry.stableKey());
        parts.add(entry.questionId());
        parts.add(entry.questionVersionId());
        parts.add(Integer.toString(entry.versionNo()));
        parts.add(entry.contentHash());
        parts.add(entry.category());
        parts.add(entry.title());
        parts.add(entry.stem());
        appendList(parts, entry.answerPoints());
        appendList(parts, entry.misconceptions());
        appendList(parts, entry.followUpTemplates());
        parts.add(entry.difficulty());
        appendList(parts, entry.targetRoles());
        parts.add(entry.locale());
        parts.add(entry.sourceId());
        parts.add(entry.sourceVersionId());
        parts.add(entry.sourceHash());
        parts.add(entry.sourceLicenseCode());
        parts.add(entry.sourceVerificationFactId());
        parts.add(entry.sourceVerifiedAt());
    }

    private static void appendList(List<String> parts, List<String> values) {
        if (values == null) {
            parts.add(null);
            return;
        }
        parts.add(Integer.toString(values.size()));
        parts.addAll(values);
    }

    private void validateManifestShape(ImportManifest manifest, List<Violation> violations,
                                       boolean requireTenant) {
        validateToken(manifest.batchId(), "batchId", MAX_BATCH_ID_LENGTH, 1, violations,
                "BATCH_ID_INVALID");
        validateToken(manifest.idempotencyKey(), "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH,
                MIN_IDEMPOTENCY_KEY_LENGTH, violations, "IDEMPOTENCY_KEY_INVALID");

        if (requireTenant && isBlank(manifest.catalogTenantId()) && isBlank(configuredTenantId)) {
            violations.add(Violation.of("TENANT_REQUIRED", "catalogTenantId", "catalog tenant is required"));
        } else if (!isBlank(manifest.catalogTenantId())) {
            validateToken(manifest.catalogTenantId(), "catalogTenantId", MAX_ID_LENGTH, 1, violations,
                    "TENANT_INVALID");
        } else if (requireTenant && !isBlank(configuredTenantId)) {
            validateToken(configuredTenantId, "configuredTenantId", MAX_ID_LENGTH, 1, violations,
                    "TENANT_INVALID");
        }

        if (manifest.declaredQuestionCount() != EXPECTED_QUESTION_COUNT) {
            violations.add(Violation.of("QUESTION_COUNT_INVALID", "declaredQuestionCount",
                    "declared question count must be exactly " + EXPECTED_QUESTION_COUNT));
        }
        if (manifest.questions() == null || manifest.questions().size() != EXPECTED_QUESTION_COUNT) {
            violations.add(Violation.of("QUESTION_COUNT_MISMATCH", "questions",
                    "manifest must contain exactly " + EXPECTED_QUESTION_COUNT + " questions"));
        }

        validateModules(manifest.modules(), violations);
        validateEntries(manifest.questions(), violations);
        validateCategoryCoverage(manifest.questions(), violations);

        String expectedFingerprint = manifest.fingerprint();
        if (!isSha256(expectedFingerprint)) {
            violations.add(Violation.of("FINGERPRINT_INVALID", "fingerprint",
                    "manifest fingerprint must be lowercase SHA-256"));
        } else if (!expectedFingerprint.equals(fingerprintOf(manifest))) {
            violations.add(Violation.of("FINGERPRINT_MISMATCH", "fingerprint",
                    "manifest fingerprint does not match canonical content"));
        }
    }

    private static void validateModules(List<String> modules, List<Violation> violations) {
        if (modules == null) {
            violations.add(Violation.of("MODULES_REQUIRED", "modules", "module list is required"));
            return;
        }
        if (modules.size() != EXPECTED_MODULE_COUNT) {
            violations.add(Violation.of("MODULE_COUNT_INVALID", "modules",
                    "manifest must declare exactly " + EXPECTED_MODULE_COUNT + " modules"));
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < modules.size(); index++) {
            String module = modules.get(index);
            if (isBlank(module)) {
                violations.add(Violation.of("MODULE_INVALID", "modules[" + index + "]",
                        "module must not be blank"));
                continue;
            }
            if (!seen.add(module)) {
                violations.add(Violation.of("MODULE_DUPLICATE", "modules[" + index + "]",
                        "module must be unique"));
            }
            if (!FIXED_MODULE_SET.contains(module)) {
                violations.add(Violation.of("MODULE_UNSUPPORTED", "modules[" + index + "]",
                        "module is not one of the fixed V2 modules"));
            }
        }
        if (!seen.equals(FIXED_MODULE_SET)) {
            violations.add(Violation.of("MODULE_SET_MISMATCH", "modules",
                    "module set must equal the twelve fixed V2 modules"));
        }
    }

    private static void validateEntries(List<QuestionEntry> entries, List<Violation> violations) {
        if (entries == null) {
            violations.add(Violation.of("QUESTIONS_REQUIRED", "questions", "question list is required"));
            return;
        }
        Set<String> stableKeys = new HashSet<>();
        Set<String> questionIds = new HashSet<>();
        Set<String> versionIds = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            QuestionEntry entry = entries.get(index);
            String path = "questions[" + index + "]";
            if (entry == null) {
                violations.add(Violation.of("QUESTION_ENTRY_REQUIRED", path, "question entry is required"));
                continue;
            }
            validateToken(entry.stableKey(), path + ".stableKey", MAX_STABLE_KEY_LENGTH, 1, violations,
                    "STABLE_KEY_INVALID");
            if (!isBlank(entry.stableKey()) && !stableKeys.add(entry.stableKey())) {
                violations.add(Violation.of("STABLE_KEY_DUPLICATE", path + ".stableKey",
                        "stable key must be unique"));
            }
            validateToken(entry.questionId(), path + ".questionId", MAX_ID_LENGTH, 1, violations,
                    "QUESTION_ID_INVALID");
            if (!isBlank(entry.questionId()) && !questionIds.add(entry.questionId())) {
                violations.add(Violation.of("QUESTION_ID_DUPLICATE", path + ".questionId",
                        "question id must be unique"));
            }
            validateToken(entry.questionVersionId(), path + ".questionVersionId", MAX_ID_LENGTH, 1, violations,
                    "QUESTION_VERSION_ID_INVALID");
            if (!isBlank(entry.questionVersionId()) && !versionIds.add(entry.questionVersionId())) {
                violations.add(Violation.of("QUESTION_VERSION_ID_DUPLICATE", path + ".questionVersionId",
                        "question version id must be unique"));
            }
            if (entry.versionNo() <= 0) {
                violations.add(Violation.of("QUESTION_VERSION_NO_INVALID", path + ".versionNo",
                        "question version number must be positive"));
            }
            if (!isSha256(entry.contentHash())) {
                violations.add(Violation.of("QUESTION_HASH_INVALID", path + ".contentHash",
                        "question content hash must be lowercase SHA-256"));
            }
            if (isBlank(entry.category()) || !FIXED_MODULE_SET.contains(entry.category())) {
                violations.add(Violation.of("QUESTION_CATEGORY_INVALID", path + ".category",
                        "question category must be one of the twelve fixed modules"));
            }
            validateText(entry.title(), path + ".title", MAX_TITLE_LENGTH, violations, "TITLE_INVALID");
            validateText(entry.stem(), path + ".stem", MAX_STEM_LENGTH, violations, "STEM_INVALID");
            validateAnswerList(entry.answerPoints(), path + ".answerPoints", true, violations);
            validateAnswerList(entry.misconceptions(), path + ".misconceptions", false, violations);
            validateAnswerList(entry.followUpTemplates(), path + ".followUpTemplates", false, violations);
            if (isBlank(entry.difficulty()) || !ALLOWED_DIFFICULTIES.contains(entry.difficulty())) {
                violations.add(Violation.of("DIFFICULTY_INVALID", path + ".difficulty",
                        "difficulty must be JUNIOR, MID or SENIOR"));
            }
            validateTargetRoles(entry.targetRoles(), path + ".targetRoles", violations);
            if (!"zh-CN".equals(entry.locale())) {
                violations.add(Violation.of("LOCALE_INVALID", path + ".locale", "locale must be zh-CN"));
            }
            validateToken(entry.sourceId(), path + ".sourceId", MAX_ID_LENGTH, 1, violations,
                    "SOURCE_ID_INVALID");
            validateToken(entry.sourceVersionId(), path + ".sourceVersionId", MAX_ID_LENGTH, 1, violations,
                    "SOURCE_VERSION_ID_INVALID");
            if (!isSha256(entry.sourceHash())) {
                violations.add(Violation.of("SOURCE_HASH_INVALID", path + ".sourceHash",
                        "source hash must be lowercase SHA-256"));
            }
            validateText(entry.sourceLicenseCode(), path + ".sourceLicenseCode", 96, violations,
                    "SOURCE_LICENSE_INVALID");
            validateToken(entry.sourceVerificationFactId(), path + ".sourceVerificationFactId", MAX_ID_LENGTH, 1,
                    violations, "SOURCE_VERIFICATION_FACT_INVALID");
            if (isBlank(entry.sourceVerifiedAt())) {
                violations.add(Violation.of("SOURCE_VERIFIED_AT_INVALID", path + ".sourceVerifiedAt",
                        "source verification time is required"));
            } else {
                try {
                    Instant.parse(entry.sourceVerifiedAt());
                } catch (DateTimeParseException exception) {
                    violations.add(Violation.of("SOURCE_VERIFIED_AT_INVALID", path + ".sourceVerifiedAt",
                            "source verification time must be ISO-8601"));
                }
            }
        }
    }

    private static void validateCategoryCoverage(List<QuestionEntry> entries, List<Violation> violations) {
        if (entries == null) {
            return;
        }
        Set<String> present = new HashSet<>();
        for (QuestionEntry entry : entries) {
            if (entry != null && !isBlank(entry.category())) {
                present.add(entry.category());
            }
        }
        for (String module : FIXED_MODULES) {
            if (!present.contains(module)) {
                violations.add(Violation.of("MODULE_WITHOUT_QUESTIONS", "questions",
                        "each fixed V2 module must contain at least one question"));
            }
        }
    }

    private static void validateTargetRoles(List<String> roles, String path, List<Violation> violations) {
        if (roles == null || roles.isEmpty()) {
            violations.add(Violation.of("TARGET_ROLES_INVALID", path, "at least one target role is required"));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < roles.size(); index++) {
            String role = roles.get(index);
            if (isBlank(role) || !ALLOWED_TARGET_ROLES.contains(role)) {
                violations.add(Violation.of("TARGET_ROLE_INVALID", path + "[" + index + "]",
                        "target role is unsupported"));
            } else if (!seen.add(role)) {
                violations.add(Violation.of("TARGET_ROLE_DUPLICATE", path + "[" + index + "]",
                        "target roles must be unique"));
            }
        }
    }

    private static void validateAnswerList(List<String> values, String path, boolean required,
                                           List<Violation> violations) {
        if (values == null) {
            violations.add(Violation.of("ANSWER_STRUCTURE_INVALID", path, "answer list is required"));
            return;
        }
        if (required && values.isEmpty()) {
            violations.add(Violation.of("ANSWER_STRUCTURE_INVALID", path,
                    "answerPoints must contain at least one item"));
        }
        if (values.size() > MAX_ANSWER_ITEMS) {
            violations.add(Violation.of("ANSWER_STRUCTURE_INVALID", path,
                    "answer list must contain at most " + MAX_ANSWER_ITEMS + " items"));
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (isBlank(value) || value.length() > MAX_ANSWER_ITEM_LENGTH || hasControlCharacter(value)) {
                violations.add(Violation.of("ANSWER_ITEM_INVALID", path + "[" + index + "]",
                        "answer item must be non-blank, <=1000 characters and free of control characters"));
            } else if (!seen.add(value)) {
                violations.add(Violation.of("ANSWER_ITEM_DUPLICATE", path + "[" + index + "]",
                        "answer items must be unique"));
            }
        }
    }

    private static void validateText(String value, String path, int maxLength, List<Violation> violations,
                                     String code) {
        if (isBlank(value) || value.length() > maxLength || hasControlCharacter(value)) {
            violations.add(Violation.of(code, path,
                    "text must be non-blank, <=" + maxLength + " characters and free of control characters"));
        }
    }

    private static void validateToken(String value, String path, int maxLength, int minLength,
                                      List<Violation> violations, String code) {
        if (isBlank(value) || value.length() < minLength || value.length() > maxLength
                || !TOKEN_PATTERN.matcher(value).matches()) {
            violations.add(Violation.of(code, path, "value has an unsupported format or length"));
        }
    }

    private ReadbackCheck validateReadback(ImportManifest manifest, String tenantId, List<Violation> violations) {
        int databaseCount = 0;
        Readback readback;
        try {
            Set<String> stableKeys = manifest.questions().stream()
                    .filter(Objects::nonNull)
                    .map(QuestionEntry::stableKey)
                    .filter(value -> !isBlank(value))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            readback = readbackPort.read(tenantId, stableKeys);
        } catch (RuntimeException exception) {
            violations.add(Violation.of("READBACK_UNAVAILABLE", "database",
                    "catalog readback failed: " + exception.getClass().getSimpleName()));
            return new ReadbackCheck(0, true);
        }
        if (readback == null) {
            violations.add(Violation.of("READBACK_UNAVAILABLE", "database", "catalog readback returned null"));
            return new ReadbackCheck(0, true);
        }
        databaseCount = readback.totalCount();
        if (readback.totalCount() != EXPECTED_QUESTION_COUNT) {
            violations.add(Violation.of("READBACK_COUNT_INVALID", "database.totalCount",
                    "database must contain exactly " + EXPECTED_QUESTION_COUNT + " imported questions"));
        }
        List<PersistedQuestion> rows = readback.questions();
        if (rows == null) {
            violations.add(Violation.of("READBACK_ROW_COUNT_INVALID", "database.questions",
                    "database readback rows must not be null"));
            return new ReadbackCheck(databaseCount, true);
        }
        if (rows.size() != EXPECTED_QUESTION_COUNT) {
            violations.add(Violation.of("READBACK_ROW_COUNT_INVALID", "database.questions",
                    "database readback rows must contain exactly " + EXPECTED_QUESTION_COUNT + " questions"));
        }

        Set<String> expectedStableKeys = manifest.questions().stream()
                .map(QuestionEntry::stableKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> actualStableKeys = new HashSet<>();
        Set<String> actualQuestionIds = new HashSet<>();
        Set<String> actualVersionIds = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            PersistedQuestion row = rows.get(index);
            String path = "database.questions[" + index + "]";
            if (row == null) {
                violations.add(Violation.of("READBACK_ROW_INVALID", path, "database row is null"));
                continue;
            }
            if (!actualStableKeys.add(row.stableKey())) {
                violations.add(Violation.of("READBACK_STABLE_KEY_DUPLICATE", path + ".stableKey",
                        "database stable key must be unique"));
            }
            if (!actualQuestionIds.add(row.questionId())) {
                violations.add(Violation.of("READBACK_QUESTION_ID_DUPLICATE", path + ".questionId",
                        "database question id must be unique"));
            }
            if (!actualVersionIds.add(row.questionVersionId())) {
                violations.add(Violation.of("READBACK_VERSION_ID_DUPLICATE", path + ".questionVersionId",
                        "database question version id must be unique"));
            }
        }
        if (!expectedStableKeys.equals(actualStableKeys)) {
            violations.add(Violation.of("READBACK_STABLE_KEY_SET_MISMATCH", "database.questions",
                    "database stable keys do not match the manifest"));
        }

        java.util.Map<String, QuestionEntry> expectedByKey = new java.util.HashMap<>();
        for (QuestionEntry entry : manifest.questions()) {
            expectedByKey.put(entry.stableKey(), entry);
        }
        for (PersistedQuestion row : rows) {
            if (row == null) {
                continue;
            }
            QuestionEntry expected = expectedByKey.get(row.stableKey());
            if (expected == null) {
                continue;
            }
            String path = "database.questions[stableKey=" + row.stableKey() + "]";
            compare("questionId", expected.questionId(), row.questionId(), path, violations);
            compare("questionVersionId", expected.questionVersionId(), row.questionVersionId(), path, violations);
            compare("contentHash", expected.contentHash(), row.contentHash(), path, violations);
            compare("category", expected.category(), row.category(), path, violations);
            compare("sourceVersionId", expected.sourceVersionId(), row.sourceVersionId(), path, violations);
            compare("sourceHash", expected.sourceHash(), row.sourceHash(), path, violations);
            if (expected.versionNo() != row.versionNo()) {
                violations.add(Violation.of("READBACK_VERSION_NO_MISMATCH", path + ".versionNo",
                        "database version number does not match the manifest"));
            }
        }
        return new ReadbackCheck(databaseCount, true);
    }

    private static void compare(String field, String expected, String actual, String path,
                                List<Violation> violations) {
        if (!Objects.equals(expected, actual)) {
            violations.add(Violation.of("READBACK_" + camelToUpperSnake(field) + "_MISMATCH",
                    path + "." + field, "database value does not match the manifest"));
        }
    }

    private static String camelToUpperSnake(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private String resolvedTenantId(ImportManifest manifest) {
        return isBlank(manifest.catalogTenantId()) ? configuredTenantId : manifest.catalogTenantId();
    }

    private static ValidationResult result(List<Violation> violations, String computedFingerprint,
                                           int manifestCount, int databaseCount, boolean readbackChecked) {
        return new ValidationResult(violations.isEmpty(), computedFingerprint, manifestCount, databaseCount,
                readbackChecked, violations);
    }

    private static boolean isSha256(String value) {
        return value != null && SHA256_PATTERN.matcher(value).matches();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index)) && value.charAt(index) != '\n'
                    && value.charAt(index) != '\r' && value.charAt(index) != '\t') {
                return true;
            }
        }
        return false;
    }

    private record ReadbackCheck(int databaseCount, boolean attempted) {
        private static ReadbackCheck notAttempted() {
            return new ReadbackCheck(0, false);
        }
    }

    /** 只读数据库回读端口；实现不得在 {@link #read(String, Set)} 中执行写操作。 */
    @FunctionalInterface
    public interface ReadbackPort {
        Readback read(String catalogTenantId, Set<String> stableKeys);
    }

    /** 数据库回读的聚合结果；totalCount 与 rows 数量都必须验证，防止受限查询伪造完整回读。 */
    public record Readback(int totalCount, List<PersistedQuestion> questions) {
        public Readback {
            questions = copyAllowingNull(questions);
        }

        public static Readback empty() {
            return new Readback(0, List.of());
        }

        public static Readback of(List<PersistedQuestion> questions) {
            List<PersistedQuestion> safe = copyAllowingNull(questions);
            return new Readback(safe == null ? 0 : safe.size(), safe);
        }
    }

    /** 回读时只携带可比较的元数据，不携带题干、答案或其他敏感正文。 */
    public record PersistedQuestion(
            String stableKey,
            String questionId,
            String questionVersionId,
            int versionNo,
            String contentHash,
            String category,
            String sourceVersionId,
            String sourceHash
    ) {
    }

    /** 清单中的一条不可变题目版本记录。正文只在校验进程内使用，不写入校验结果。 */
    public record QuestionEntry(
            String stableKey,
            String questionId,
            String questionVersionId,
            int versionNo,
            String contentHash,
            String category,
            String title,
            String stem,
            List<String> answerPoints,
            List<String> misconceptions,
            List<String> followUpTemplates,
            String difficulty,
            List<String> targetRoles,
            String locale,
            String sourceId,
            String sourceVersionId,
            String sourceHash,
            String sourceLicenseCode,
            String sourceVerificationFactId,
            String sourceVerifiedAt
    ) {
        public QuestionEntry {
            answerPoints = copyAllowingNull(answerPoints);
            misconceptions = copyAllowingNull(misconceptions);
            followUpTemplates = copyAllowingNull(followUpTemplates);
            targetRoles = copyAllowingNull(targetRoles);
        }

        /** 便于导入器从已拆分的答案结构创建记录。 */
        public QuestionEntry(String stableKey, String questionId, String questionVersionId, int versionNo,
                             String contentHash, String category, String title, String stem,
                             AnswerStructure answer, String difficulty, List<String> targetRoles, String locale,
                             SourceReference source) {
            this(stableKey, questionId, questionVersionId, versionNo, contentHash, category, title, stem,
                    answer == null ? null : answer.answerPoints(), answer == null ? null : answer.misconceptions(),
                    answer == null ? null : answer.followUpTemplates(), difficulty, targetRoles, locale,
                    source == null ? null : source.sourceId(), source == null ? null : source.sourceVersionId(),
                    source == null ? null : source.sourceHash(), source == null ? null : source.licenseCode(),
                    source == null ? null : source.verificationFactId(), source == null ? null : source.verifiedAt());
        }

        public AnswerStructure answerStructure() {
            return new AnswerStructure(answerPoints, misconceptions, followUpTemplates);
        }

        public SourceReference sourceReference() {
            return new SourceReference(sourceId, sourceVersionId, sourceHash, sourceLicenseCode,
                    sourceVerificationFactId, sourceVerifiedAt);
        }
    }

    /** 题目答案的结构化部分；答案正文不会进入 ValidationResult。 */
    public record AnswerStructure(
            List<String> answerPoints,
            List<String> misconceptions,
            List<String> followUpTemplates
    ) {
        public AnswerStructure {
            answerPoints = copyAllowingNull(answerPoints);
            misconceptions = copyAllowingNull(misconceptions);
            followUpTemplates = copyAllowingNull(followUpTemplates);
        }
    }

    /** 来源治理快照；只接受已验证版本，不允许用 URL 或客户端自由文本替代版本 ID/哈希。 */
    public record SourceReference(
            String sourceId,
            String sourceVersionId,
            String sourceHash,
            String licenseCode,
            String verificationFactId,
            String verifiedAt
    ) {
    }

    /**
     * 传输/导入清单。modules 使用 List 而非 Set，以便校验器能够发现重复模块。
     */
    public record ImportManifest(
            String batchId,
            String idempotencyKey,
            String catalogTenantId,
            int declaredQuestionCount,
            List<String> modules,
            List<QuestionEntry> questions,
            String fingerprint
    ) {
        public ImportManifest {
            modules = copyAllowingNull(modules);
            questions = copyAllowingNull(questions);
        }

        /** 兼容不携带目标租户的离线结构预检；执行数据库回读时必须提供租户。 */
        public ImportManifest(String batchId, String idempotencyKey, int declaredQuestionCount,
                              List<String> modules, List<QuestionEntry> questions, String fingerprint) {
            this(batchId, idempotencyKey, null, declaredQuestionCount, modules, questions, fingerprint);
        }

        /** 生成同一清单内容的指纹副本，不改变原对象。 */
        public ImportManifest withComputedFingerprint() {
            return new ImportManifest(batchId, idempotencyKey, catalogTenantId, declaredQuestionCount,
                    modules, questions, CatalogImportManifestValidator.fingerprintOf(this));
        }
    }

    /** 单项可审计校验错误；消息不包含题干、答案或来源凭据。 */
    public record Violation(String code, String path, String message) {
        public Violation {
            code = Objects.requireNonNull(code, "code");
            path = Objects.requireNonNull(path, "path");
            message = Objects.requireNonNull(message, "message");
        }

        private static Violation of(String code, String path, String message) {
            return new Violation(code, path, message);
        }
    }

    /** 校验结果；valid=true 仅表示结构和数据库回读均无错误，不表示已发布。 */
    public record ValidationResult(
            boolean valid,
            String computedFingerprint,
            int manifestQuestionCount,
            int databaseQuestionCount,
            boolean readbackChecked,
            List<Violation> violations
    ) {
        public ValidationResult {
            violations = List.copyOf(violations == null ? List.of() : violations);
        }

        public boolean passed() {
            return valid;
        }
    }

    private static <T> List<T> copyAllowingNull(Collection<T> values) {
        if (values == null) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
