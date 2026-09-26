package com.ruoyi.interview.infrastructure.persistence.practice;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.LengthPrefixedCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.practice.port.PracticeRepository;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.domain.practice.AnswerSource;
import com.ruoyi.interview.domain.practice.AnswerVersion;
import com.ruoyi.interview.domain.practice.DraftAnswer;
import com.ruoyi.interview.domain.practice.PracticeAttempt;
import com.ruoyi.interview.domain.practice.PracticeAttemptState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@InterviewEnabled
@Repository
@Transactional(transactionManager = "interviewTransactionManager", readOnly = true)
public class JdbcPracticeRepository implements PracticeRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;

    public JdbcPracticeRepository(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public Optional<PracticeAttempt> find(TenantId tenantId, ResourceId attemptId) {
        return jdbc.query("""
                select * from practice.attempt
                 where tenant_id = :tenantId and attempt_id = :attemptId
                """, Map.of("tenantId", tenantId.value(), "attemptId", attemptId.value()),
                (resultSet, rowNum) -> mapAttempt(resultSet)).stream().findFirst();
    }

    @Override
    public Page findHistory(
            TenantId tenantId,
            UserId userId,
            Optional<String> cursor,
            int limit
    ) {
        Optional<Cursor> decoded = (cursor == null ? Optional.<String>empty() : cursor)
                .map(this::decodeCursor);
        decoded.ifPresent(value -> requireOwnedCursor(tenantId, userId, value));
        StringBuilder sql = new StringBuilder("""
                select a.*
                  from practice.attempt a
                 where a.tenant_id = :tenantId and a.user_id = :userId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("userId", userId.value())
                .addValue("limit", limit + 1);
        decoded.ifPresent(value -> {
            sql.append(" and (a.started_at, a.attempt_id) < (:cursorAt, :cursorId)");
            parameters.addValue("cursorAt", value.startedAt())
                    .addValue("cursorId", value.attemptId().value());
        });
        sql.append(" order by a.started_at desc, a.attempt_id desc limit :limit");
        List<AttemptRow> rows = jdbc.query(sql.toString(), parameters,
                (resultSet, rowNum) -> new AttemptRow(
                        mapAttempt(resultSet), JdbcPersistenceSupport.readInstant(resultSet, "started_at")));
        boolean hasMore = rows.size() > limit;
        List<PracticeAttempt> items = rows.stream().limit(limit).map(AttemptRow::attempt).toList();
        Optional<String> nextCursor = hasMore && !items.isEmpty()
                ? Optional.of(encodeCursor(tenantId, userId, rows.get(limit - 1)))
                : Optional.empty();
        return new Page(items, nextCursor);
    }

    @Override
    @Transactional(transactionManager = "interviewTransactionManager", propagation = Propagation.MANDATORY)
    public void save(PracticeAttempt attempt) {
        EncryptedEnvelope draftEnvelope = attempt.draft()
                .map(draft -> cipher.encrypt(attempt.tenantId(), draftBinding(attempt.id()), draft.text()))
                .orElse(null);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", attempt.tenantId().value())
                .addValue("attemptId", attempt.id().value())
                .addValue("userId", attempt.userId().value())
                .addValue("questionVersionId", attempt.questionVersion().resourceId().value())
                .addValue("questionVersionNo", attempt.questionVersion().versionNo())
                .addValue("questionHash", attempt.questionVersion().contentHash())
                .addValue("rubricVersionId", attempt.rubricVersion().resourceId().value())
                .addValue("rubricVersionNo", attempt.rubricVersion().versionNo())
                .addValue("rubricHash", attempt.rubricVersion().contentHash())
                .addValue("state", attempt.state().name())
                .addValue("startedAt", attempt.startedAt())
                .addValue("submittedAt", attempt.submittedAt())
                .addValue("cancelledAt", attempt.cancelledAt())
                .addValue("draftHash", attempt.draft().map(DraftAnswer::contentHash).orElse(null))
                .addValue("draftSavedAt", attempt.draft().map(DraftAnswer::savedAt).orElse(null))
                .addValue("version", attempt.version().value());
        JdbcPersistenceSupport.addEnvelope(parameters, "draft", draftEnvelope);
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into practice.attempt (
                    tenant_id, attempt_id, user_id,
                    question_version_id, question_version_no, question_content_hash,
                    rubric_version_id, rubric_version_no, rubric_content_hash,
                    state, started_at, submitted_at, cancelled_at,
                    draft_content_hash, draft_saved_at, draft_key_id, draft_algorithm,
                    draft_nonce, draft_ciphertext, draft_aad_hash, aggregate_version
                ) values (
                    :tenantId, :attemptId, :userId,
                    :questionVersionId, :questionVersionNo, :questionHash,
                    :rubricVersionId, :rubricVersionNo, :rubricHash,
                    :state, :startedAt, :submittedAt, :cancelledAt,
                    :draftHash, :draftSavedAt, :draftKeyId, :draftAlgorithm,
                    :draftNonce, :draftCiphertext, :draftAadHash, :version
                ) on conflict do nothing
                """, """
                update practice.attempt
                   set state = :state, submitted_at = :submittedAt, cancelled_at = :cancelledAt,
                       draft_content_hash = :draftHash, draft_saved_at = :draftSavedAt,
                       draft_key_id = :draftKeyId, draft_algorithm = :draftAlgorithm,
                       draft_nonce = :draftNonce, draft_ciphertext = :draftCiphertext,
                       draft_aad_hash = :draftAadHash, aggregate_version = :version
                 where tenant_id = :tenantId and attempt_id = :attemptId
                   and aggregate_version = :expectedVersion
                """, parameters, attempt.version().value(),
                "practice attempt " + attempt.tenantId().value() + "/" + attempt.id().value());

        attempt.answerVersions().forEach(this::appendAnswerVersion);
    }

    private PracticeAttempt mapAttempt(ResultSet resultSet) throws SQLException {
        TenantId tenantId = TenantId.of(resultSet.getString("tenant_id"));
        ResourceId attemptId = ResourceId.of(resultSet.getString("attempt_id"));
        EncryptedEnvelope draftEnvelope = JdbcPersistenceSupport.readNullableEnvelope(resultSet, "draft");
        Optional<DraftAnswer> draft = draftEnvelope == null
                ? Optional.empty()
                : Optional.of(new DraftAnswer(
                        cipher.decrypt(tenantId, draftBinding(attemptId), draftEnvelope),
                        resultSet.getString("draft_content_hash"),
                        JdbcPersistenceSupport.readInstant(resultSet, "draft_saved_at")));
        List<AnswerVersion> answers = jdbc.query("""
                select * from practice.answer_version
                 where tenant_id = :tenantId and attempt_id = :attemptId
                 order by version_no
                """, Map.of("tenantId", tenantId.value(), "attemptId", attemptId.value()),
                (answerRow, rowNum) -> mapAnswer(answerRow));
        return PracticeAttempt.rehydrate(
                attemptId, tenantId, UserId.of(resultSet.getString("user_id")),
                immutableRef(resultSet, "question"), immutableRef(resultSet, "rubric"),
                JdbcPersistenceSupport.readInstant(resultSet, "started_at"),
                PracticeAttemptState.valueOf(resultSet.getString("state")), draft, answers,
                JdbcPersistenceSupport.readNullableInstant(resultSet, "submitted_at"),
                JdbcPersistenceSupport.readNullableInstant(resultSet, "cancelled_at"),
                new AggregateVersion(resultSet.getLong("aggregate_version")));
    }

    private AnswerVersion mapAnswer(ResultSet resultSet) throws SQLException {
        TenantId tenantId = TenantId.of(resultSet.getString("tenant_id"));
        ResourceId answerId = ResourceId.of(resultSet.getString("answer_version_id"));
        String supersedes = resultSet.getString("supersedes_id");
        String text = cipher.decrypt(tenantId, answerBinding(answerId),
                JdbcPersistenceSupport.readEnvelope(resultSet, "body"));
        return new AnswerVersion(answerId, tenantId,
                ResourceId.of(resultSet.getString("attempt_id")), resultSet.getInt("version_no"),
                AnswerSource.valueOf(resultSet.getString("source")), text,
                resultSet.getString("content_hash"), UserId.of(resultSet.getString("submitted_by")),
                JdbcPersistenceSupport.readInstant(resultSet, "submitted_at"),
                supersedes == null ? Optional.empty() : Optional.of(ResourceId.of(supersedes)));
    }

    private void appendAnswerVersion(AnswerVersion answer) {
        EncryptedEnvelope body = cipher.encrypt(answer.tenantId(), answerBinding(answer.id()), answer.text());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", answer.tenantId().value())
                .addValue("answerId", answer.id().value())
                .addValue("attemptId", answer.attemptId().value())
                .addValue("versionNo", answer.versionNo())
                .addValue("source", answer.source().name())
                .addValue("contentHash", answer.contentHash())
                .addValue("submittedBy", answer.submittedBy().value())
                .addValue("submittedAt", answer.submittedAt())
                .addValue("supersedesId", answer.supersedesId().map(ResourceId::value).orElse(null));
        JdbcPersistenceSupport.addEnvelope(parameters, "body", body);
        int inserted = jdbc.update("""
                insert into practice.answer_version (
                    tenant_id, answer_version_id, attempt_id, version_no, source, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash,
                    submitted_by, submitted_at, supersedes_id
                ) values (
                    :tenantId, :answerId, :attemptId, :versionNo, :source, :contentHash,
                    :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash,
                    :submittedBy, :submittedAt, :supersedesId
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            Boolean identical = jdbc.queryForObject("""
                    select content_hash = :contentHash and attempt_id = :attemptId
                           and version_no = :versionNo and source = :source
                           and submitted_by = :submittedBy and submitted_at = :submittedAt
                           and supersedes_id is not distinct from :supersedesId
                      from practice.answer_version
                     where tenant_id = :tenantId and answer_version_id = :answerId
                    """, parameters, Boolean.class);
            if (!Boolean.TRUE.equals(identical)) {
                throw new DataIntegrityViolationException("immutable practice answer id collision");
            }
        }
    }

    private static ImmutableVersionRef immutableRef(ResultSet resultSet, String prefix) throws SQLException {
        return new ImmutableVersionRef(ResourceId.of(resultSet.getString(prefix + "_version_id")),
                resultSet.getInt(prefix + "_version_no"), resultSet.getString(prefix + "_content_hash"));
    }

    private static String draftBinding(ResourceId attemptId) {
        return "practice.attempt:" + attemptId.value() + ":draft";
    }

    private static String answerBinding(ResourceId answerId) {
        return "practice.answer-version:" + answerId.value() + ":body";
    }

    private void requireOwnedCursor(TenantId tenantId, UserId userId, Cursor cursor) {
        if (!tenantId.equals(cursor.tenantId()) || !userId.equals(cursor.userId())) {
            throw invalidCursor();
        }
        Boolean exists = jdbc.queryForObject("""
                select exists (
                    select 1 from practice.attempt
                     where tenant_id = :tenantId and user_id = :userId
                       and attempt_id = :attemptId and started_at = :startedAt
                )
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("userId", userId.value())
                .addValue("attemptId", cursor.attemptId().value())
                .addValue("startedAt", cursor.startedAt()), Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw invalidCursor();
        }
    }

    private String encodeCursor(TenantId tenantId, UserId userId, AttemptRow row) {
        String encoded = LengthPrefixedCodec.encode(List.of(
                tenantId.value(), userId.value(), row.startedAt().toString(), row.attempt().id().value()));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(encoded.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        try {
            if (value == null || value.isBlank() || value.length() > 512) {
                throw new IllegalArgumentException();
            }
            List<String> fields = LengthPrefixedCodec.decode(new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
            if (fields.size() != 4) {
                throw new IllegalArgumentException();
            }
            return new Cursor(TenantId.of(fields.get(0)), UserId.of(fields.get(1)),
                    Instant.parse(fields.get(2)), ResourceId.of(fields.get(3)));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("invalid practice history cursor");
    }

    private record Cursor(TenantId tenantId, UserId userId, Instant startedAt, ResourceId attemptId) { }

    private record AttemptRow(PracticeAttempt attempt, Instant startedAt) { }
}

