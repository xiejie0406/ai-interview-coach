package com.ruoyi.interview.infrastructure.persistence.evaluation;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.evaluation.FeedbackContentPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 反馈正文的加密 owner；返回值只是一条受控引用，不包含用户原文。 */
@InterviewEnabled
@Repository
@Transactional(readOnly = true)
public class JdbcFeedbackContentRepository implements FeedbackContentPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;

    public JdbcFeedbackContentRepository(
            NamedParameterJdbcTemplate jdbc,
            SensitiveEnvelopeCipher cipher
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.cipher = java.util.Objects.requireNonNull(cipher);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String store(
            TenantId tenantId,
            UserId userId,
            ResourceId evaluationId,
            ResourceId feedbackId,
            String comment
    ) {
        String contentRef = contentReference(feedbackId);
        EncryptedEnvelope envelope = cipher.encrypt(tenantId, binding(feedbackId), comment);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("feedbackId", feedbackId.value())
                .addValue("evaluationId", evaluationId.value()).addValue("userId", userId.value())
                .addValue("contentRef", contentRef);
        JdbcPersistenceSupport.addEnvelope(parameters, "content", envelope);
        jdbc.update("""
                insert into evaluation.feedback_content (
                    tenant_id, feedback_id, evaluation_id, user_id, content_ref,
                    content_key_id, content_algorithm, content_nonce,
                    content_ciphertext, content_aad_hash
                ) values (
                    :tenantId, :feedbackId, :evaluationId, :userId, :contentRef,
                    :contentKeyId, :contentAlgorithm, :contentNonce,
                    :contentCiphertext, :contentAadHash
                ) on conflict do nothing
                """, parameters);

        List<StoredContent> stored = jdbc.query("""
                select * from evaluation.feedback_content
                 where tenant_id = :tenantId and feedback_id = :feedbackId
                """, parameters, (row, rowNum) -> new StoredContent(
                row.getString("evaluation_id"), row.getString("user_id"), row.getString("content_ref"),
                cipher.decrypt(tenantId, binding(feedbackId),
                        JdbcPersistenceSupport.readEnvelope(row, "content"))));
        if (stored.size() != 1
                || !evaluationId.value().equals(stored.get(0).evaluationId())
                || !userId.value().equals(stored.get(0).userId())
                || !contentRef.equals(stored.get(0).contentRef())
                || !comment.equals(stored.get(0).comment())) {
            throw new DataIntegrityViolationException("immutable feedback content id collision: "
                    + tenantId.value() + "/" + feedbackId.value());
        }
        return contentRef;
    }

    private static String binding(ResourceId feedbackId) {
        return "evaluation.feedback-content:" + feedbackId.value() + ":comment";
    }

    private static String contentReference(ResourceId feedbackId) {
        return "evaluation.feedback-content:" + feedbackId.value();
    }

    private record StoredContent(String evaluationId, String userId, String contentRef, String comment) {
        @Override
        public String toString() {
            return "StoredContent[redacted]";
        }
    }
}

