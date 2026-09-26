package com.ruoyi.interview.infrastructure.persistence.learning;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.LengthPrefixedCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.learning.LearningPlanRepository;
import com.ruoyi.interview.domain.learning.LearningItemStatus;
import com.ruoyi.interview.domain.learning.LearningPlan;
import com.ruoyi.interview.domain.learning.LearningPlanStatus;
import com.ruoyi.interview.domain.learning.LearningTask;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** LearningPlan/Item 的 JDBC owner；支持 plan 一次合法前进两个 aggregate version。 */
@InterviewEnabled
@Repository
@Transactional(transactionManager = "interviewTransactionManager", readOnly = true)
public class JdbcLearningPlanRepository implements LearningPlanRepository {

    private static final String ITEM_CONTENT_FORMAT = "learning-item-content-v1";
    private static final String LIMITATIONS_FORMAT = "learning-limitations-v1";

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;
    private final PersistenceJsonCodec json;

    public JdbcLearningPlanRepository(
            NamedParameterJdbcTemplate jdbc,
            SensitiveEnvelopeCipher cipher,
            PersistenceJsonCodec json
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.cipher = java.util.Objects.requireNonNull(cipher);
        this.json = java.util.Objects.requireNonNull(json);
    }

    @Override
    public Optional<LearningPlan> find(TenantId tenantId, ResourceId learningPlanId) {
        return jdbc.query("""
                select * from learning.plan
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                """, Map.of("tenantId", tenantId.value(), "learningPlanId", learningPlanId.value()),
                (row, rowNum) -> mapPlan(row)).stream().findFirst();
    }

    @Override
    public Optional<LearningPlan> findByItem(TenantId tenantId, ResourceId learningItemId) {
        return jdbc.query("""
                select p.*
                  from learning.plan p
                  join learning.item i
                    on i.tenant_id = p.tenant_id and i.learning_plan_id = p.learning_plan_id
                 where p.tenant_id = :tenantId and i.learning_item_id = :learningItemId
                """, Map.of("tenantId", tenantId.value(), "learningItemId", learningItemId.value()),
                (row, rowNum) -> mapPlan(row)).stream().findFirst();
    }

    @Override
    public Page findByOwner(
            TenantId tenantId,
            UserId userId,
            Optional<String> cursor,
            int limit
    ) {
        Optional<Cursor> decoded = (cursor == null ? Optional.<String>empty() : cursor).map(this::decodeCursor);
        decoded.ifPresent(value -> requireOwnedCursor(tenantId, userId, value));
        StringBuilder sql = new StringBuilder("""
                select * from learning.plan
                 where tenant_id = :tenantId and user_id = :userId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("userId", userId.value())
                .addValue("limit", limit + 1);
        decoded.ifPresent(value -> {
            sql.append(" and (created_at, learning_plan_id) < (:cursorAt, :cursorId)");
            parameters.addValue("cursorAt", value.createdAt()).addValue("cursorId", value.planId().value());
        });
        sql.append(" order by created_at desc, learning_plan_id desc limit :limit");
        List<PlanRow> rows = jdbc.query(sql.toString(), parameters,
                (row, rowNum) -> new PlanRow(mapPlan(row),
                        JdbcPersistenceSupport.readInstant(row, "created_at")));
        boolean hasMore = rows.size() > limit;
        List<LearningPlan> items = rows.stream().limit(limit).map(PlanRow::plan).toList();
        Optional<String> next = hasMore && !items.isEmpty()
                ? Optional.of(encodeCursor(rows.get(limit - 1))) : Optional.empty();
        return new Page(items, next);
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public void save(LearningPlan plan) {
        EncryptedEnvelope limitations = cipher.encrypt(plan.tenantId(), limitationsBinding(plan.id()),
                encodeList(LIMITATIONS_FORMAT, plan.limitations()));
        ProviderPolicySnapshot provider = plan.providerPolicySnapshot();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", plan.tenantId().value()).addValue("learningPlanId", plan.id().value())
                .addValue("userId", plan.userId().value())
                .addValue("sourceReportId", plan.sourceReportId().value())
                .addValue("sourceReportVersionId", plan.sourceReportVersionId().value())
                .addValue("configVersionId", plan.configVersionId())
                .addValue("promptKey", plan.coachSchemaPin().promptKey())
                .addValue("promptVersion", plan.coachSchemaPin().promptVersion())
                .addValue("schemaKey", plan.coachSchemaPin().schemaKey())
                .addValue("schemaVersion", plan.coachSchemaPin().schemaVersion())
                .addValue("routePlanId", provider.routePlanId())
                .addValue("primaryProvider", provider.primaryProvider())
                .addValue("primaryModel", provider.primaryModel())
                .addValue("fallbackProvider", provider.fallbackProvider())
                .addValue("fallbackModel", provider.fallbackModel())
                .addValue("safetyFlags", json.write(provider.safetyFlags()))
                .addValue("status", plan.status().name())
                .addValue("confirmedAt", plan.confirmedAt().orElse(null))
                .addValue("completedAt", plan.completedAt().orElse(null))
                .addValue("version", plan.version().value());
        JdbcPersistenceSupport.addEnvelope(parameters, "limitations", limitations);
        int inserted = jdbc.update("""
                insert into learning.plan (
                    tenant_id, learning_plan_id, user_id,
                    source_report_id, source_report_version_id, config_version_id,
                    coach_prompt_key, coach_prompt_version, coach_schema_key, coach_schema_version,
                    provider_route_plan_id, primary_provider, primary_model,
                    fallback_provider, fallback_model, safety_flags,
                    limitations_key_id, limitations_algorithm, limitations_nonce,
                    limitations_ciphertext, limitations_aad_hash,
                    status, confirmed_at, completed_at, aggregate_version
                ) values (
                    :tenantId, :learningPlanId, :userId,
                    :sourceReportId, :sourceReportVersionId, :configVersionId,
                    :promptKey, :promptVersion, :schemaKey, :schemaVersion,
                    :routePlanId, :primaryProvider, :primaryModel,
                    :fallbackProvider, :fallbackModel, cast(:safetyFlags as jsonb),
                    :limitationsKeyId, :limitationsAlgorithm, :limitationsNonce,
                    :limitationsCiphertext, :limitationsAadHash,
                    :status, :confirmedAt, :completedAt, :version
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            updateExistingPlan(plan, parameters);
        }

        for (int index = 0; index < plan.items().size(); index++) {
            saveItem(plan, plan.items().get(index), index + 1);
        }
        Long itemCount = jdbc.queryForObject("""
                select count(*) from learning.item
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                """, parameters, Long.class);
        if (itemCount == null || itemCount != plan.items().size()) {
            throw new DataIntegrityViolationException("learning plan item set is inconsistent");
        }
        LearningPlan stored = find(plan.tenantId(), plan.id()).orElseThrow(() ->
                new DataIntegrityViolationException("saved learning plan cannot be reloaded"));
        if (!samePlan(plan, stored)) {
            throw new DataIntegrityViolationException("learning plan persistence round-trip mismatch");
        }
    }

    private void updateExistingPlan(LearningPlan plan, MapSqlParameterSource parameters) {
        StoredPlanHeader stored = loadHeader(plan.tenantId(), plan.id());
        if (!stored.sameIdentity(plan)) {
            throw new DataIntegrityViolationException("learning plan id collision: "
                    + plan.tenantId().value() + "/" + plan.id().value());
        }
        long persistedVersion = stored.version();
        long nextVersion = plan.version().value();
        if (nextVersion == persistedVersion) {
            if (!stored.sameState(plan)) {
                throw new OptimisticLockingFailureException("learning plan state changed without a new version");
            }
            return;
        }
        if (nextVersion <= persistedVersion || nextVersion - persistedVersion > 2) {
            throw new OptimisticLockingFailureException("learning plan aggregate version conflict");
        }
        parameters.addValue("persistedVersion", persistedVersion);
        int updated = jdbc.update("""
                update learning.plan
                   set status = :status, confirmed_at = :confirmedAt, completed_at = :completedAt,
                       aggregate_version = :version
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                   and aggregate_version = :persistedVersion
                """, parameters);
        if (updated != 1) {
            throw new OptimisticLockingFailureException("learning plan aggregate version conflict");
        }
    }

    private StoredPlanHeader loadHeader(TenantId tenantId, ResourceId planId) {
        List<StoredPlanHeader> values = jdbc.query("""
                select * from learning.plan
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                """, Map.of("tenantId", tenantId.value(), "learningPlanId", planId.value()),
                (row, rowNum) -> {
                    String encoded = cipher.decrypt(tenantId, limitationsBinding(planId),
                            JdbcPersistenceSupport.readEnvelope(row, "limitations"));
                    return new StoredPlanHeader(UserId.of(row.getString("user_id")),
                            ResourceId.of(row.getString("source_report_id")),
                            ResourceId.of(row.getString("source_report_version_id")),
                            row.getString("config_version_id"), coachPin(row), provider(row),
                            decodeList(LIMITATIONS_FORMAT, encoded),
                            LearningPlanStatus.valueOf(row.getString("status")),
                            JdbcPersistenceSupport.readNullableInstant(row, "confirmed_at"),
                            JdbcPersistenceSupport.readNullableInstant(row, "completed_at"),
                            row.getLong("aggregate_version"));
                });
        if (values.size() != 1) {
            throw new DataIntegrityViolationException("learning plan conflict did not resolve to its primary key");
        }
        return values.get(0);
    }

    private LearningPlan mapPlan(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId planId = ResourceId.of(row.getString("learning_plan_id"));
        List<LearningTask> items = jdbc.query("""
                select * from learning.item
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                 order by position
                """, Map.of("tenantId", tenantId.value(), "learningPlanId", planId.value()),
                (itemRow, rowNum) -> mapItem(tenantId, planId, itemRow, rowNum));
        String limitations = cipher.decrypt(tenantId, limitationsBinding(planId),
                JdbcPersistenceSupport.readEnvelope(row, "limitations"));
        return LearningPlan.rehydrate(planId, tenantId, UserId.of(row.getString("user_id")),
                ResourceId.of(row.getString("source_report_id")),
                ResourceId.of(row.getString("source_report_version_id")),
                row.getString("config_version_id"), coachPin(row), provider(row), items,
                decodeList(LIMITATIONS_FORMAT, limitations),
                LearningPlanStatus.valueOf(row.getString("status")),
                JdbcPersistenceSupport.readNullableInstant(row, "confirmed_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "completed_at"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private LearningTask mapItem(
            TenantId tenantId,
            ResourceId planId,
            ResultSet row,
            int rowIndex
    ) throws SQLException {
        if (row.getInt("position") != rowIndex + 1) {
            throw new DataIntegrityViolationException("learning item positions are not contiguous");
        }
        ResourceId itemId = ResourceId.of(row.getString("learning_item_id"));
        List<String> content = decodeItemContent(cipher.decrypt(tenantId, itemBinding(planId, itemId),
                JdbcPersistenceSupport.readEnvelope(row, "content")));
        return new LearningTask(itemId, ResourceId.of(row.getString("question_version_id")),
                content.get(0), content.get(1), json.readStringList(row.getString("reason_codes")),
                row.getInt("priority"),
                Optional.ofNullable(JdbcPersistenceSupport.readNullableInstant(row, "scheduled_at")),
                LearningItemStatus.valueOf(row.getString("status")),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private void saveItem(LearningPlan plan, LearningTask item, int position) {
        String encoded = encodeItemContent(item);
        EncryptedEnvelope content = cipher.encrypt(plan.tenantId(), itemBinding(plan.id(), item.itemId()), encoded);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", plan.tenantId().value()).addValue("learningPlanId", plan.id().value())
                .addValue("learningItemId", item.itemId().value()).addValue("position", position)
                .addValue("questionVersionId", item.questionVersionId().value())
                .addValue("reasonCodes", json.write(item.reasonCodes())).addValue("priority", item.priority())
                .addValue("scheduledAt", item.scheduledAt().orElse(null)).addValue("status", item.state().name())
                .addValue("version", item.version().value());
        JdbcPersistenceSupport.addEnvelope(parameters, "content", content);
        int inserted = jdbc.update("""
                insert into learning.item (
                    tenant_id, learning_plan_id, learning_item_id, position, question_version_id,
                    content_key_id, content_algorithm, content_nonce, content_ciphertext,
                    content_aad_hash, reason_codes, priority, scheduled_at, status, aggregate_version
                ) values (
                    :tenantId, :learningPlanId, :learningItemId, :position, :questionVersionId,
                    :contentKeyId, :contentAlgorithm, :contentNonce, :contentCiphertext,
                    :contentAadHash, cast(:reasonCodes as jsonb), :priority, :scheduledAt, :status, :version
                ) on conflict do nothing
                """, parameters);
        if (inserted == 1) {
            return;
        }
        StoredItem stored = loadItem(plan.tenantId(), plan.id(), item.itemId());
        if (!stored.sameIdentity(item, position)) {
            throw new DataIntegrityViolationException("learning item id collision: "
                    + plan.tenantId().value() + "/" + item.itemId().value());
        }
        if (item.version().value() == stored.version()) {
            if (!stored.sameState(item)) {
                throw new OptimisticLockingFailureException("learning item state changed without a new version");
            }
            return;
        }
        if (item.version().value() != stored.version() + 1) {
            throw new OptimisticLockingFailureException("learning item aggregate version conflict");
        }
        parameters.addValue("persistedVersion", stored.version());
        int updated = jdbc.update("""
                update learning.item
                   set scheduled_at = :scheduledAt, status = :status, aggregate_version = :version
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                   and learning_item_id = :learningItemId and aggregate_version = :persistedVersion
                """, parameters);
        if (updated != 1) {
            throw new OptimisticLockingFailureException("learning item aggregate version conflict");
        }
    }

    private StoredItem loadItem(TenantId tenantId, ResourceId planId, ResourceId itemId) {
        List<StoredItem> values = jdbc.query("""
                select * from learning.item
                 where tenant_id = :tenantId and learning_plan_id = :learningPlanId
                   and learning_item_id = :learningItemId
                """, new MapSqlParameterSource().addValue("tenantId", tenantId.value())
                .addValue("learningPlanId", planId.value()).addValue("learningItemId", itemId.value()),
                (row, rowNum) -> {
                    List<String> content = decodeItemContent(cipher.decrypt(tenantId,
                            itemBinding(planId, itemId), JdbcPersistenceSupport.readEnvelope(row, "content")));
                    return new StoredItem(row.getInt("position"),
                            ResourceId.of(row.getString("question_version_id")), content.get(0), content.get(1),
                            json.readStringList(row.getString("reason_codes")), row.getInt("priority"),
                            JdbcPersistenceSupport.readNullableInstant(row, "scheduled_at"),
                            LearningItemStatus.valueOf(row.getString("status")),
                            row.getLong("aggregate_version"));
                });
        if (values.size() != 1) {
            throw new DataIntegrityViolationException("learning item conflict did not resolve to its owner plan");
        }
        return values.get(0);
    }

    private void requireOwnedCursor(TenantId tenantId, UserId userId, Cursor cursor) {
        Boolean exists = jdbc.queryForObject("""
                select exists (
                    select 1 from learning.plan
                     where tenant_id = :tenantId and user_id = :userId
                       and learning_plan_id = :learningPlanId and created_at = :createdAt
                )
                """, new MapSqlParameterSource().addValue("tenantId", tenantId.value())
                .addValue("userId", userId.value()).addValue("learningPlanId", cursor.planId().value())
                .addValue("createdAt", cursor.createdAt()), Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new IllegalArgumentException("invalid learning plan cursor");
        }
    }

    private String encodeCursor(PlanRow row) {
        String value = LengthPrefixedCodec.encode(List.of(
                row.createdAt().toString(), row.plan().id().value()));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        try {
            List<String> fields = LengthPrefixedCodec.decode(new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
            if (fields.size() != 2) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(fields.get(0)), ResourceId.of(fields.get(1)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid learning plan cursor", exception);
        }
    }

    private ProviderPolicySnapshot provider(ResultSet row) throws SQLException {
        return new ProviderPolicySnapshot(row.getString("provider_route_plan_id"),
                row.getString("primary_provider"), row.getString("primary_model"),
                row.getString("fallback_provider"), row.getString("fallback_model"),
                json.readStringMap(row.getString("safety_flags")));
    }

    private static PromptSchemaPin coachPin(ResultSet row) throws SQLException {
        return new PromptSchemaPin(row.getString("coach_prompt_key"), row.getInt("coach_prompt_version"),
                row.getString("coach_schema_key"), row.getInt("coach_schema_version"));
    }

    private static boolean samePlan(LearningPlan expected, LearningPlan actual) {
        return expected.id().equals(actual.id()) && expected.tenantId().equals(actual.tenantId())
                && expected.userId().equals(actual.userId())
                && expected.sourceReportId().equals(actual.sourceReportId())
                && expected.sourceReportVersionId().equals(actual.sourceReportVersionId())
                && expected.configVersionId().equals(actual.configVersionId())
                && expected.coachSchemaPin().equals(actual.coachSchemaPin())
                && expected.providerPolicySnapshot().equals(actual.providerPolicySnapshot())
                && expected.items().equals(actual.items()) && expected.limitations().equals(actual.limitations())
                && expected.status() == actual.status() && expected.confirmedAt().equals(actual.confirmedAt())
                && expected.completedAt().equals(actual.completedAt()) && expected.version().equals(actual.version());
    }

    private static String encodeItemContent(LearningTask item) {
        return LengthPrefixedCodec.encode(List.of(
                ITEM_CONTENT_FORMAT, item.questionTitle(), item.weaknessRef()));
    }

    private static List<String> decodeItemContent(String value) {
        List<String> fields = LengthPrefixedCodec.decode(value);
        if (fields.size() != 3 || !ITEM_CONTENT_FORMAT.equals(fields.get(0))) {
            throw new DataIntegrityViolationException("unsupported learning item content format");
        }
        return List.of(fields.get(1), fields.get(2));
    }

    private static String encodeList(String format, List<String> values) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>(values.size() + 1);
        fields.add(format);
        fields.addAll(values);
        return LengthPrefixedCodec.encode(fields);
    }

    private static List<String> decodeList(String format, String value) {
        List<String> fields = LengthPrefixedCodec.decode(value);
        if (fields.isEmpty() || !format.equals(fields.get(0))) {
            throw new DataIntegrityViolationException("unsupported encrypted learning list format");
        }
        return List.copyOf(fields.subList(1, fields.size()));
    }

    private static String limitationsBinding(ResourceId planId) {
        return "learning.plan:" + planId.value() + ":limitations";
    }

    private static String itemBinding(ResourceId planId, ResourceId itemId) {
        return "learning.plan:" + planId.value() + ":item:" + itemId.value() + ":content";
    }

    private record Cursor(Instant createdAt, ResourceId planId) { }

    private record PlanRow(LearningPlan plan, Instant createdAt) { }

    private record StoredPlanHeader(
            UserId userId,
            ResourceId sourceReportId,
            ResourceId sourceReportVersionId,
            String configVersionId,
            PromptSchemaPin coachPin,
            ProviderPolicySnapshot provider,
            List<String> limitations,
            LearningPlanStatus status,
            Instant confirmedAt,
            Instant completedAt,
            long version
    ) {
        private boolean sameIdentity(LearningPlan plan) {
            return userId.equals(plan.userId()) && sourceReportId.equals(plan.sourceReportId())
                    && sourceReportVersionId.equals(plan.sourceReportVersionId())
                    && configVersionId.equals(plan.configVersionId())
                    && coachPin.equals(plan.coachSchemaPin())
                    && provider.equals(plan.providerPolicySnapshot())
                    && limitations.equals(plan.limitations());
        }

        private boolean sameState(LearningPlan plan) {
            return status == plan.status()
                    && Optional.ofNullable(confirmedAt).equals(plan.confirmedAt())
                    && Optional.ofNullable(completedAt).equals(plan.completedAt())
                    && version == plan.version().value();
        }
    }

    private record StoredItem(
            int position,
            ResourceId questionVersionId,
            String questionTitle,
            String weaknessRef,
            List<String> reasonCodes,
            int priority,
            Instant scheduledAt,
            LearningItemStatus status,
            long version
    ) {
        private boolean sameIdentity(LearningTask item, int expectedPosition) {
            return position == expectedPosition && questionVersionId.equals(item.questionVersionId())
                    && questionTitle.equals(item.questionTitle()) && weaknessRef.equals(item.weaknessRef())
                    && reasonCodes.equals(item.reasonCodes()) && priority == item.priority();
        }

        private boolean sameState(LearningTask item) {
            return Optional.ofNullable(scheduledAt).equals(item.scheduledAt())
                    && status == item.state() && version == item.version().value();
        }
    }
}

