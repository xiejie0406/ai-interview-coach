package com.ruoyi.interview.infrastructure.persistence.catalog;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.LengthPrefixedCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.catalog.PublishedQuestionSnapshot;
import com.ruoyi.interview.application.catalog.PublishedQuestionSummary;
import com.ruoyi.interview.application.catalog.QuestionSearchCriteria;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.domain.catalog.ContentSourceReference;
import com.ruoyi.interview.domain.catalog.Question;
import com.ruoyi.interview.domain.catalog.QuestionPublication;
import com.ruoyi.interview.domain.catalog.QuestionStatus;
import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.catalog.RubricDimension;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class JdbcCatalogRepository implements CatalogRepository, PublishedQuestionPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;
    private final PersistenceJsonCodec json;

    public JdbcCatalogRepository(
            NamedParameterJdbcTemplate jdbc,
            SensitiveEnvelopeCipher cipher,
            PersistenceJsonCodec json
    ) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
    }

    @Override
    public Optional<Question> findQuestion(TenantId tenantId, ResourceId questionId) {
        return jdbc.query("""
                select * from catalog.question
                 where tenant_id = :tenantId and question_id = :questionId
                """, Map.of("tenantId", tenantId.value(), "questionId", questionId.value()),
                (row, rowNum) -> mapQuestion(row)).stream().findFirst();
    }

    @Override
    public Optional<Question> findByStableKey(TenantId tenantId, String stableKey) {
        return jdbc.query("""
                select * from catalog.question
                 where tenant_id = :tenantId and stable_key = :stableKey
                """, Map.of("tenantId", tenantId.value(), "stableKey", stableKey),
                (row, rowNum) -> mapQuestion(row)).stream().findFirst();
    }

    @Override
    public Optional<QuestionVersion> findQuestionVersion(TenantId tenantId, ResourceId questionVersionId) {
        return jdbc.query("""
                select * from catalog.question_version
                 where tenant_id = :tenantId and question_version_id = :versionId
                """, Map.of("tenantId", tenantId.value(), "versionId", questionVersionId.value()),
                (row, rowNum) -> mapQuestionVersion(row)).stream().findFirst();
    }

    @Override
    public Optional<RubricVersion> findRubricVersion(TenantId tenantId, ResourceId rubricVersionId) {
        return jdbc.query("""
                select * from catalog.rubric_version
                 where tenant_id = :tenantId and rubric_version_id = :versionId
                """, Map.of("tenantId", tenantId.value(), "versionId", rubricVersionId.value()),
                (row, rowNum) -> mapRubricVersion(row)).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveQuestion(Question question) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", question.tenantId().value())
                .addValue("questionId", question.id().value())
                .addValue("stableKey", question.stableKey())
                .addValue("status", question.status().name())
                .addValue("version", question.version().value());
        addVersionRef(parameters, "draft", question.currentDraftVersion());
        addVersionRef(parameters, "published", question.currentPublishedVersion());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into catalog.question (
                    tenant_id, question_id, stable_key, status,
                    draft_version_id, draft_version_no, draft_content_hash,
                    published_version_id, published_version_no, published_content_hash,
                    aggregate_version
                ) values (
                    :tenantId, :questionId, :stableKey, :status,
                    :draftVersionId, :draftVersionNo, :draftContentHash,
                    :publishedVersionId, :publishedVersionNo, :publishedContentHash,
                    :version
                ) on conflict do nothing
                """, """
                update catalog.question
                   set status = :status,
                       draft_version_id = :draftVersionId, draft_version_no = :draftVersionNo,
                       draft_content_hash = :draftContentHash,
                       published_version_id = :publishedVersionId,
                       published_version_no = :publishedVersionNo,
                       published_content_hash = :publishedContentHash,
                       aggregate_version = :version
                 where tenant_id = :tenantId and question_id = :questionId
                   and aggregate_version = :expectedVersion
                """, parameters, question.version().value(),
                "catalog question " + question.tenantId().value() + "/" + question.id().value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendQuestionVersion(QuestionVersion version) {
        QuestionBody body = new QuestionBody(version.title(), version.stem(), version.answerPoints(),
                version.misconceptions(), version.followUpTemplates());
        EncryptedEnvelope envelope = cipher.encrypt(version.tenantId(), questionBodyBinding(version.id()),
                encodeQuestionBody(body));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", version.tenantId().value())
                .addValue("versionId", version.id().value())
                .addValue("questionId", version.questionId().value())
                .addValue("versionNo", version.versionNo())
                .addValue("contentHash", version.contentHash())
                .addValue("difficulty", version.difficulty())
                .addValue("targetRoles", json.write(version.targetRoles()))
                .addValue("locale", version.locale())
                .addValue("authoredBy", version.authoredBy().value())
                .addValue("reviewedBy", version.reviewedBy().map(UserId::value).orElse(null))
                .addValue("createdAt", version.createdAt());
        JdbcPersistenceSupport.addEnvelope(parameters, "body", envelope);
        addSource(parameters, version.source());
        int inserted = jdbc.update("""
                insert into catalog.question_version (
                    tenant_id, question_version_id, question_id, version_no, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash,
                    difficulty, target_roles, locale,
                    source_id, source_version_id, source_version_no, source_content_hash,
                    source_license_code, source_verification_fact_id, source_verified_at,
                    authored_by, reviewed_by, created_at
                ) values (
                    :tenantId, :versionId, :questionId, :versionNo, :contentHash,
                    :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash,
                    :difficulty, cast(:targetRoles as jsonb), :locale,
                    :sourceId, :sourceVersionId, :sourceVersionNo, :sourceContentHash,
                    :sourceLicense, :sourceVerificationId, :sourceVerifiedAt,
                    :authoredBy, :reviewedBy, :createdAt
                ) on conflict do nothing
                """, parameters);
        verifyQuestionVersionInsert(inserted, version, parameters);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendRubricVersion(RubricVersion version) {
        RubricBody body = new RubricBody(version.dimensions(), version.refusalPolicy());
        EncryptedEnvelope envelope = cipher.encrypt(version.tenantId(), rubricBodyBinding(version.id()),
                encodeRubricBody(body));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", version.tenantId().value())
                .addValue("versionId", version.id().value())
                .addValue("questionVersionId", version.questionVersionId().value())
                .addValue("versionNo", version.versionNo())
                .addValue("contentHash", version.contentHash())
                .addValue("createdAt", version.createdAt());
        JdbcPersistenceSupport.addEnvelope(parameters, "body", envelope);
        int inserted = jdbc.update("""
                insert into catalog.rubric_version (
                    tenant_id, rubric_version_id, question_version_id, version_no, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash, created_at
                ) values (
                    :tenantId, :versionId, :questionVersionId, :versionNo, :contentHash,
                    :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash, :createdAt
                ) on conflict do nothing
                """, parameters);
        verifyRubricVersionInsert(inserted, version, parameters);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendPublication(QuestionPublication publication) {
        jdbc.update("""
                insert into catalog.question_publication (
                    tenant_id, publication_id, question_id,
                    question_version_id, question_version_no, question_content_hash,
                    rubric_version_id, rubric_version_no, rubric_content_hash,
                    source_verification_fact_id, reviewed_by, reason_code, published_at
                ) values (
                    :tenantId, :publicationId, :questionId,
                    :questionVersionId, :questionVersionNo, :questionHash,
                    :rubricVersionId, :rubricVersionNo, :rubricHash,
                    :verificationId, :reviewedBy, :reasonCode, :publishedAt
                )
                """, new MapSqlParameterSource()
                .addValue("tenantId", publication.tenantId().value())
                .addValue("publicationId", publication.id().value())
                .addValue("questionId", publication.questionId().value())
                .addValue("questionVersionId", publication.questionVersion().resourceId().value())
                .addValue("questionVersionNo", publication.questionVersion().versionNo())
                .addValue("questionHash", publication.questionVersion().contentHash())
                .addValue("rubricVersionId", publication.rubricVersion().resourceId().value())
                .addValue("rubricVersionNo", publication.rubricVersion().versionNo())
                .addValue("rubricHash", publication.rubricVersion().contentHash())
                .addValue("verificationId", publication.sourceVerificationFactId().value())
                .addValue("reviewedBy", publication.reviewedBy().value())
                .addValue("reasonCode", publication.reasonCode())
                .addValue("publishedAt", publication.publishedAt()));
    }

    @Override
    public Optional<PublishedQuestionSnapshot> findPublished(TenantId tenantId, ResourceId questionId) {
        return jdbc.query("""
                select q.question_id, qv.*, p.rubric_version_id as published_rubric_id,
                       p.rubric_version_no as published_rubric_no,
                       p.rubric_content_hash as published_rubric_hash
                  from catalog.question q
                  join catalog.question_version qv
                    on qv.tenant_id = q.tenant_id and qv.question_version_id = q.published_version_id
                  join catalog.question_publication p
                    on p.tenant_id = q.tenant_id and p.question_version_id = qv.question_version_id
                 where q.tenant_id = :tenantId and q.question_id = :questionId
                   and q.status in ('PUBLISHED','PUBLISHED_WITH_DRAFT','PUBLISHED_WITH_REVIEW')
                """, Map.of("tenantId", tenantId.value(), "questionId", questionId.value()),
                (row, rowNum) -> mapSnapshot(row)).stream().findFirst();
    }

    @Override
    public Optional<PublishedQuestionSnapshot> findPublishedVersion(
            TenantId tenantId,
            ImmutableVersionRef questionVersion,
            ImmutableVersionRef rubricVersion
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("questionVersionId", questionVersion.resourceId().value())
                .addValue("questionVersionNo", questionVersion.versionNo())
                .addValue("questionHash", questionVersion.contentHash())
                .addValue("rubricVersionId", rubricVersion.resourceId().value())
                .addValue("rubricVersionNo", rubricVersion.versionNo())
                .addValue("rubricHash", rubricVersion.contentHash());
        return jdbc.query("""
                select p.question_id, qv.*, p.rubric_version_id as published_rubric_id,
                       p.rubric_version_no as published_rubric_no,
                       p.rubric_content_hash as published_rubric_hash
                  from catalog.question_publication p
                  join catalog.question_version qv
                    on qv.tenant_id = p.tenant_id and qv.question_version_id = p.question_version_id
                 where p.tenant_id = :tenantId
                   and p.question_version_id = :questionVersionId
                   and p.question_version_no = :questionVersionNo
                   and p.question_content_hash = :questionHash
                   and p.rubric_version_id = :rubricVersionId
                   and p.rubric_version_no = :rubricVersionNo
                   and p.rubric_content_hash = :rubricHash
                """, parameters, (row, rowNum) -> mapSnapshot(row)).stream().findFirst();
    }

    @Override
    public CursorPage<PublishedQuestionSummary> searchPublished(
            TenantId tenantId,
            QuestionSearchCriteria criteria
    ) {
        // 首版 category 映射到 targetRoles；题干正文是加密字段，关键词在解密后的只读投影上过滤。
        StringBuilder sql = new StringBuilder("""
                select q.question_id, qv.*
                  from catalog.question q
                  join catalog.question_version qv
                    on qv.tenant_id = q.tenant_id and qv.question_version_id = q.published_version_id
                 where q.tenant_id = :tenantId
                   and q.status in ('PUBLISHED','PUBLISHED_WITH_DRAFT','PUBLISHED_WITH_REVIEW')
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("limit", criteria.keyword().isPresent() ? 1000 : criteria.limit() + 1);
        criteria.cursor().ifPresent(cursor -> {
            sql.append(" and q.question_id > :cursorQuestionId");
            parameters.addValue("cursorQuestionId", decodeCursor(cursor));
        });
        criteria.locale().ifPresent(locale -> {
            sql.append(" and qv.locale = :locale");
            parameters.addValue("locale", locale);
        });
        if (!criteria.difficulties().isEmpty()) {
            sql.append(" and qv.difficulty in (:difficulties)");
            parameters.addValue("difficulties", criteria.difficulties());
        }
        if (!criteria.targetRoles().isEmpty()) {
            sql.append(" and exists (select 1 from jsonb_array_elements_text(qv.target_roles) r(value) ")
                    .append("where r.value in (:targetRoles))");
            parameters.addValue("targetRoles", criteria.targetRoles());
        }
        sql.append(" order by q.question_id limit :limit");
        List<PublishedQuestionSummary> rows = jdbc.query(sql.toString(), parameters,
                (row, rowNum) -> mapSummary(row));
        criteria.keyword().ifPresent(keyword -> {
            String normalized = keyword.trim().toLowerCase(java.util.Locale.ROOT);
            rows.removeIf(item -> !item.title().toLowerCase(java.util.Locale.ROOT).contains(normalized));
        });
        boolean hasMore = rows.size() > criteria.limit();
        List<PublishedQuestionSummary> items = hasMore
                ? List.copyOf(rows.subList(0, criteria.limit())) : List.copyOf(rows);
        Optional<String> next = hasMore && !items.isEmpty()
                ? Optional.of(encodeCursor(items.getLast().questionId().value())) : Optional.empty();
        return new CursorPage<>(items, next);
    }

    private Question mapQuestion(ResultSet row) throws SQLException {
        return Question.rehydrate(ResourceId.of(row.getString("question_id")),
                TenantId.of(row.getString("tenant_id")), row.getString("stable_key"),
                QuestionStatus.valueOf(row.getString("status")),
                readVersionRef(row, "draft"), readVersionRef(row, "published"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private QuestionVersion mapQuestionVersion(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId versionId = ResourceId.of(row.getString("question_version_id"));
        QuestionBody body = decodeQuestionBody(cipher.decrypt(tenantId, questionBodyBinding(versionId),
                JdbcPersistenceSupport.readEnvelope(row, "body")));
        return new QuestionVersion(versionId, tenantId, ResourceId.of(row.getString("question_id")),
                row.getInt("version_no"), row.getString("content_hash"), body.title(), body.stem(),
                body.answerPoints(), body.misconceptions(), body.followUpTemplates(),
                row.getString("difficulty"), json.readStringList(row.getString("target_roles")),
                row.getString("locale"), readSource(row), UserId.of(row.getString("authored_by")),
                optionalUser(row.getString("reviewed_by")),
                JdbcPersistenceSupport.readInstant(row, "created_at"));
    }

    private RubricVersion mapRubricVersion(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId versionId = ResourceId.of(row.getString("rubric_version_id"));
        RubricBody body = decodeRubricBody(cipher.decrypt(tenantId, rubricBodyBinding(versionId),
                JdbcPersistenceSupport.readEnvelope(row, "body")));
        return new RubricVersion(versionId, tenantId,
                ResourceId.of(row.getString("question_version_id")), row.getInt("version_no"),
                row.getString("content_hash"), body.dimensions(), body.refusalPolicy(),
                JdbcPersistenceSupport.readInstant(row, "created_at"));
    }

    private PublishedQuestionSnapshot mapSnapshot(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId versionId = ResourceId.of(row.getString("question_version_id"));
        QuestionBody body = decodeQuestionBody(cipher.decrypt(tenantId, questionBodyBinding(versionId),
                JdbcPersistenceSupport.readEnvelope(row, "body")));
        return new PublishedQuestionSnapshot(ResourceId.of(row.getString("question_id")),
                new ImmutableVersionRef(versionId, row.getInt("version_no"), row.getString("content_hash")),
                new ImmutableVersionRef(ResourceId.of(row.getString("published_rubric_id")),
                        row.getInt("published_rubric_no"), row.getString("published_rubric_hash")),
                body.title(), body.stem(), body.answerPoints(), body.followUpTemplates(),
                row.getString("difficulty"), json.readStringList(row.getString("target_roles")),
                row.getString("locale"));
    }

    private PublishedQuestionSummary mapSummary(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId versionId = ResourceId.of(row.getString("question_version_id"));
        QuestionBody body = decodeQuestionBody(cipher.decrypt(tenantId, questionBodyBinding(versionId),
                JdbcPersistenceSupport.readEnvelope(row, "body")));
        return new PublishedQuestionSummary(ResourceId.of(row.getString("question_id")),
                new ImmutableVersionRef(versionId, row.getInt("version_no"), row.getString("content_hash")),
                body.title(), row.getString("difficulty"),
                json.readStringList(row.getString("target_roles")), row.getString("locale"));
    }

    private Optional<ContentSourceReference> readSource(ResultSet row) throws SQLException {
        String sourceId = row.getString("source_id");
        if (sourceId == null) {
            return Optional.empty();
        }
        return Optional.of(new ContentSourceReference(ResourceId.of(sourceId),
                new ImmutableVersionRef(ResourceId.of(row.getString("source_version_id")),
                        row.getInt("source_version_no"), row.getString("source_content_hash")),
                row.getString("source_license_code"),
                ResourceId.of(row.getString("source_verification_fact_id")),
                JdbcPersistenceSupport.readInstant(row, "source_verified_at")));
    }

    private static void addSource(
            MapSqlParameterSource parameters,
            Optional<ContentSourceReference> source
    ) {
        ContentSourceReference value = source.orElse(null);
        parameters.addValue("sourceId", value == null ? null : value.sourceId().value())
                .addValue("sourceVersionId", value == null ? null : value.sourceVersion().resourceId().value())
                .addValue("sourceVersionNo", value == null ? null : value.sourceVersion().versionNo())
                .addValue("sourceContentHash", value == null ? null : value.sourceVersion().contentHash())
                .addValue("sourceLicense", value == null ? null : value.licenseCode())
                .addValue("sourceVerificationId", value == null ? null : value.verificationFactId().value())
                .addValue("sourceVerifiedAt", value == null ? null : value.verifiedAt());
    }

    private static void addVersionRef(
            MapSqlParameterSource parameters,
            String prefix,
            Optional<ImmutableVersionRef> reference
    ) {
        ImmutableVersionRef value = reference.orElse(null);
        parameters.addValue(prefix + "VersionId", value == null ? null : value.resourceId().value())
                .addValue(prefix + "VersionNo", value == null ? null : value.versionNo())
                .addValue(prefix + "ContentHash", value == null ? null : value.contentHash());
    }

    private static Optional<ImmutableVersionRef> readVersionRef(ResultSet row, String prefix)
            throws SQLException {
        String id = row.getString(prefix + "_version_id");
        return id == null ? Optional.empty() : Optional.of(new ImmutableVersionRef(ResourceId.of(id),
                row.getInt(prefix + "_version_no"), row.getString(prefix + "_content_hash")));
    }

    private void verifyQuestionVersionInsert(
            int inserted,
            QuestionVersion version,
            MapSqlParameterSource parameters
    ) {
        if (inserted == 1) {
            return;
        }
        Boolean identical = jdbc.queryForObject("""
                select question_id = :questionId
                       and version_no = :versionNo and content_hash = :contentHash
                       and difficulty = :difficulty and target_roles = cast(:targetRoles as jsonb)
                       and locale = :locale
                       and source_id is not distinct from :sourceId
                       and source_version_id is not distinct from :sourceVersionId
                       and source_version_no is not distinct from :sourceVersionNo
                       and source_content_hash is not distinct from :sourceContentHash
                       and source_license_code is not distinct from :sourceLicense
                       and source_verification_fact_id is not distinct from :sourceVerificationId
                       and source_verified_at is not distinct from :sourceVerifiedAt
                       and authored_by = :authoredBy
                       and reviewed_by is not distinct from :reviewedBy
                       and created_at = :createdAt
                  from catalog.question_version
                 where tenant_id = :tenantId and question_version_id = :versionId
                """, parameters, Boolean.class);
        if (!Boolean.TRUE.equals(identical)) {
            throw new DataIntegrityViolationException(
                    "immutable question version id collision: " + version.tenantId().value()
                            + "/" + version.id().value());
        }
    }

    private void verifyRubricVersionInsert(
            int inserted,
            RubricVersion version,
            MapSqlParameterSource parameters
    ) {
        if (inserted == 1) {
            return;
        }
        Boolean identical = jdbc.queryForObject("""
                select question_version_id = :questionVersionId
                       and version_no = :versionNo and content_hash = :contentHash
                       and created_at = :createdAt
                  from catalog.rubric_version
                 where tenant_id = :tenantId and rubric_version_id = :versionId
                """, parameters, Boolean.class);
        if (!Boolean.TRUE.equals(identical)) {
            throw new DataIntegrityViolationException(
                    "immutable rubric version id collision: " + version.tenantId().value()
                            + "/" + version.id().value());
        }
    }

    private static Optional<UserId> optionalUser(String value) {
        return value == null ? Optional.empty() : Optional.of(UserId.of(value));
    }

    private static String questionBodyBinding(ResourceId versionId) {
        return "catalog.question-version:" + versionId.value() + ":body";
    }

    private static String rubricBodyBinding(ResourceId versionId) {
        return "catalog.rubric-version:" + versionId.value() + ":body";
    }

    private static String encodeQuestionBody(QuestionBody body) {
        List<String> values = new ArrayList<>();
        values.add(body.title());
        values.add(body.stem());
        appendList(values, body.answerPoints());
        appendList(values, body.misconceptions());
        appendList(values, body.followUpTemplates());
        return LengthPrefixedCodec.encode(values);
    }

    private static QuestionBody decodeQuestionBody(String encoded) {
        ValueCursor cursor = new ValueCursor(LengthPrefixedCodec.decode(encoded));
        QuestionBody result = new QuestionBody(cursor.next(), cursor.next(),
                cursor.nextList(), cursor.nextList(), cursor.nextList());
        cursor.requireEnd();
        return result;
    }

    private static String encodeRubricBody(RubricBody body) {
        List<String> values = new ArrayList<>();
        values.add(body.refusalPolicy());
        values.add(Integer.toString(body.dimensions().size()));
        for (RubricDimension dimension : body.dimensions()) {
            values.add(dimension.code());
            values.add(dimension.description());
            values.add(Boolean.toString(dimension.evidenceRequired()));
            appendList(values, dimension.criteria());
        }
        return LengthPrefixedCodec.encode(values);
    }

    private static RubricBody decodeRubricBody(String encoded) {
        ValueCursor cursor = new ValueCursor(LengthPrefixedCodec.decode(encoded));
        String refusalPolicy = cursor.next();
        int dimensionCount = cursor.nextCount();
        List<RubricDimension> dimensions = new ArrayList<>(dimensionCount);
        for (int index = 0; index < dimensionCount; index++) {
            dimensions.add(new RubricDimension(cursor.next(), cursor.next(),
                    cursor.nextBoolean(), cursor.nextList()));
        }
        cursor.requireEnd();
        return new RubricBody(List.copyOf(dimensions), refusalPolicy);
    }

    private static void appendList(List<String> target, List<String> values) {
        target.add(Integer.toString(values.size()));
        target.addAll(values);
    }

    private static String encodeCursor(String questionId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(questionId.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeCursor(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid catalog cursor", exception);
        }
    }

    private record QuestionBody(
            String title,
            String stem,
            List<String> answerPoints,
            List<String> misconceptions,
            List<String> followUpTemplates
    ) { }

    private record RubricBody(List<RubricDimension> dimensions, String refusalPolicy) { }

    private static final class ValueCursor {
        private final List<String> values;
        private int offset;

        private ValueCursor(List<String> values) {
            this.values = values;
        }

        private String next() {
            if (offset >= values.size()) {
                throw new DataIntegrityViolationException("encrypted catalog body is truncated");
            }
            return values.get(offset++);
        }

        private int nextCount() {
            try {
                int count = Integer.parseInt(next());
                if (count < 0 || count > values.size() - offset) {
                    throw new NumberFormatException();
                }
                return count;
            } catch (NumberFormatException exception) {
                throw new DataIntegrityViolationException("encrypted catalog list count is invalid", exception);
            }
        }

        private List<String> nextList() {
            int count = nextCount();
            List<String> result = List.copyOf(values.subList(offset, offset + count));
            offset += count;
            return result;
        }

        private boolean nextBoolean() {
            String value = next();
            if ("true".equals(value)) {
                return true;
            }
            if ("false".equals(value)) {
                return false;
            }
            throw new DataIntegrityViolationException("encrypted catalog boolean is invalid");
        }

        private void requireEnd() {
            if (offset != values.size()) {
                throw new DataIntegrityViolationException("encrypted catalog body contains trailing fields");
            }
        }
    }
}

