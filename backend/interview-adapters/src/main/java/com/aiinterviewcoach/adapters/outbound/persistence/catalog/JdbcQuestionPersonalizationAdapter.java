package com.aiinterviewcoach.adapters.outbound.persistence.catalog;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.EncryptedEnvelope;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.LengthPrefixedCodec;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.PersistenceJsonCodec;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.SensitiveEnvelopeCipher;
import com.aiinterviewcoach.application.catalog.port.QuestionPersonalizationPort;
import com.aiinterviewcoach.application.platform.ServerSideDigest;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcQuestionPersonalizationAdapter implements QuestionPersonalizationPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;
    private final PersistenceJsonCodec json;

    public JdbcQuestionPersonalizationAdapter(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher,
                                              PersistenceJsonCodec json) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
    }

    @Override
    @Transactional
    public ResourceId createPublicQuestion(CreatePublicQuestion command) {
        String publicAuthorUserId = publicAuthor(command.catalogTenantId());
        String questionId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        String rubricId = UUID.randomUUID().toString();
        String publicationId = UUID.randomUUID().toString();
        String verificationId = UUID.randomUUID().toString();
        String sourceId = UUID.randomUUID().toString();
        String sourceVersionId = UUID.randomUUID().toString();
        String contentHash = ServerSideDigest.sha256(command.title(), command.prompt(), command.systemAnswer());
        String rubricHash = ServerSideDigest.sha256("user-public-rubric", command.systemAnswer());
        String sourceHash = ServerSideDigest.sha256("user-public-source", questionId);

        jdbc.update("""
                insert into catalog.question (tenant_id, question_id, stable_key, status, aggregate_version)
                values (:tenantId, :questionId, :stableKey, 'DRAFT', 0)
                """, new MapSqlParameterSource()
                .addValue("tenantId", command.catalogTenantId().value()).addValue("questionId", questionId)
                .addValue("stableKey", "user-public-" + questionId));

        List<String> bodyValues = new ArrayList<>();
        bodyValues.add(command.title());
        bodyValues.add(command.prompt());
        appendList(bodyValues, List.of(command.systemAnswer()));
        appendList(bodyValues, List.of());
        appendList(bodyValues, List.of());
        EncryptedEnvelope body = cipher.encrypt(command.catalogTenantId(),
                "catalog.question-version:" + versionId + ":body", LengthPrefixedCodec.encode(bodyValues));
        MapSqlParameterSource version = new MapSqlParameterSource()
                .addValue("tenantId", command.catalogTenantId().value()).addValue("versionId", versionId)
                .addValue("questionId", questionId).addValue("contentHash", contentHash)
                .addValue("difficulty", command.difficulty()).addValue("targetRoles", json.write(List.of(command.category())))
                .addValue("sourceId", sourceId).addValue("sourceVersionId", sourceVersionId)
                .addValue("sourceHash", sourceHash).addValue("verificationId", verificationId)
                .addValue("author", publicAuthorUserId)
                .addValue("now", JdbcPersistenceSupport.writeInstant(command.now()));
        JdbcPersistenceSupport.addEnvelope(version, "body", body);
        jdbc.update("""
                insert into catalog.question_version (
                    tenant_id, question_version_id, question_id, version_no, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash,
                    difficulty, target_roles, locale, source_id, source_version_id, source_version_no,
                    source_content_hash, source_license_code, source_verification_fact_id, source_verified_at,
                    authored_by, reviewed_by, created_at)
                values (:tenantId, :versionId, :questionId, 1, :contentHash,
                    :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash,
                    :difficulty, cast(:targetRoles as jsonb), 'zh-CN', :sourceId, :sourceVersionId, 1,
                    :sourceHash, 'SELF_AUTHORED', :verificationId, :now, :author, :author, :now)
                """, version);

        List<String> rubricValues = new ArrayList<>();
        rubricValues.add("用户公共题目的基础答案由创建者提供");
        rubricValues.add("1");
        rubricValues.add("STANDARD_ANSWER");
        rubricValues.add("答案应完整回应题目");
        rubricValues.add("true");
        appendList(rubricValues, List.of(command.systemAnswer()));
        EncryptedEnvelope rubric = cipher.encrypt(command.catalogTenantId(),
                "catalog.rubric-version:" + rubricId + ":body", LengthPrefixedCodec.encode(rubricValues));
        MapSqlParameterSource rubricParams = new MapSqlParameterSource()
                .addValue("tenantId", command.catalogTenantId().value()).addValue("rubricId", rubricId)
                .addValue("versionId", versionId).addValue("contentHash", rubricHash)
                .addValue("now", JdbcPersistenceSupport.writeInstant(command.now()));
        JdbcPersistenceSupport.addEnvelope(rubricParams, "body", rubric);
        jdbc.update("""
                insert into catalog.rubric_version (
                    tenant_id, rubric_version_id, question_version_id, version_no, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash, created_at)
                values (:tenantId, :rubricId, :versionId, 1, :contentHash,
                    :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash, :now)
                """, rubricParams);

        jdbc.update("""
                insert into catalog.question_publication (
                    tenant_id, publication_id, question_id, question_version_id, question_version_no,
                    question_content_hash, rubric_version_id, rubric_version_no, rubric_content_hash,
                    source_verification_fact_id, reviewed_by, reason_code, published_at)
                values (:tenantId, :publicationId, :questionId, :versionId, 1, :questionHash,
                    :rubricId, 1, :rubricHash, :verificationId, :author, 'USER_PUBLIC_CREATE', :now)
                """, new MapSqlParameterSource()
                .addValue("tenantId", command.catalogTenantId().value()).addValue("publicationId", publicationId)
                .addValue("questionId", questionId).addValue("versionId", versionId)
                .addValue("questionHash", contentHash).addValue("rubricId", rubricId)
                .addValue("rubricHash", rubricHash).addValue("verificationId", verificationId)
                .addValue("author", publicAuthorUserId)
                .addValue("now", JdbcPersistenceSupport.writeInstant(command.now())));
        jdbc.update("""
                update catalog.question set status = 'PUBLISHED', published_version_id = :versionId,
                    published_version_no = 1, published_content_hash = :questionHash, aggregate_version = 3
                 where tenant_id = :tenantId and question_id = :questionId
                """, new MapSqlParameterSource().addValue("tenantId", command.catalogTenantId().value())
                .addValue("questionId", questionId).addValue("versionId", versionId)
                .addValue("questionHash", contentHash));
        jdbc.update("""
                insert into catalog.public_question_creator (
                    catalog_tenant_id, question_id, creator_tenant_id, creator_user_id, created_at)
                values (:catalogTenantId, :questionId, :creatorTenantId, :creatorUserId, :now)
                """, new MapSqlParameterSource()
                .addValue("catalogTenantId", command.catalogTenantId().value())
                .addValue("questionId", questionId)
                .addValue("creatorTenantId", command.creatorTenantId().value())
                .addValue("creatorUserId", command.createdBy().value())
                .addValue("now", JdbcPersistenceSupport.writeInstant(command.now())));
        return ResourceId.of(questionId);
    }

    @Override
    public Optional<String> findUserAnswer(TenantId catalogTenantId, TenantId ownerTenantId,
                                           UserId userId, ResourceId questionId) {
        return jdbc.query("""
                select * from catalog.user_question_answer
                 where catalog_tenant_id = :catalogTenantId and owner_tenant_id = :ownerTenantId
                   and user_id = :userId and question_id = :questionId
                """, new MapSqlParameterSource().addValue("catalogTenantId", catalogTenantId.value())
                .addValue("ownerTenantId", ownerTenantId.value())
                .addValue("userId", userId.value()).addValue("questionId", questionId.value()),
                (row, rowNum) -> cipher.decrypt(ownerTenantId, answerBinding(userId, questionId),
                        JdbcPersistenceSupport.readEnvelope(row, "answer"))).stream().findFirst();
    }

    @Override
    @Transactional
    public void saveUserAnswer(TenantId catalogTenantId, TenantId ownerTenantId, UserId userId,
                               ResourceId questionId, String answer, java.time.Instant now) {
        EncryptedEnvelope envelope = cipher.encrypt(ownerTenantId, answerBinding(userId, questionId), answer);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("catalogTenantId", catalogTenantId.value()).addValue("ownerTenantId", ownerTenantId.value())
                .addValue("userId", userId.value())
                .addValue("questionId", questionId.value()).addValue("now", JdbcPersistenceSupport.writeInstant(now));
        JdbcPersistenceSupport.addEnvelope(parameters, "answer", envelope);
        jdbc.update("""
                insert into catalog.user_question_answer (
                    catalog_tenant_id, owner_tenant_id, user_id, question_id, answer_key_id, answer_algorithm,
                    answer_nonce, answer_ciphertext, answer_aad_hash, created_at, updated_at)
                values (:catalogTenantId, :ownerTenantId, :userId, :questionId, :answerKeyId, :answerAlgorithm,
                    :answerNonce, :answerCiphertext, :answerAadHash, :now, :now)
                on conflict (catalog_tenant_id, owner_tenant_id, user_id, question_id) do update set
                    answer_key_id = excluded.answer_key_id, answer_algorithm = excluded.answer_algorithm,
                    answer_nonce = excluded.answer_nonce, answer_ciphertext = excluded.answer_ciphertext,
                    answer_aad_hash = excluded.answer_aad_hash, updated_at = excluded.updated_at
                """, parameters);
    }

    private String publicAuthor(TenantId catalogTenantId) {
        return jdbc.query("""
                select user_id from identity.membership
                 where tenant_id = :tenantId and status = 'ACTIVE'
                 order by case role when 'OWNER' then 0 else 1 end, user_id
                 limit 1
                """, java.util.Map.of("tenantId", catalogTenantId.value()),
                (row, rowNum) -> row.getString("user_id")).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("public catalog tenant requires an active author"));
    }

    private static String answerBinding(UserId userId, ResourceId questionId) {
        return "catalog.user-answer:" + userId.value() + ":" + questionId.value();
    }

    private static void appendList(List<String> target, List<String> values) {
        target.add(Integer.toString(values.size()));
        target.addAll(values);
    }
}
