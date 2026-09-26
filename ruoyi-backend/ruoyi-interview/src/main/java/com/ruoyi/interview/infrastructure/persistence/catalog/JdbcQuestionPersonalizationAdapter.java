package com.ruoyi.interview.infrastructure.persistence.catalog;

import com.ruoyi.interview.configuration.InterviewEnabled;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.catalog.port.QuestionPersonalizationPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@InterviewEnabled
@Repository
public class JdbcQuestionPersonalizationAdapter implements QuestionPersonalizationPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;

    public JdbcQuestionPersonalizationAdapter(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
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
    @Transactional(transactionManager = "interviewTransactionManager")
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

    private static String answerBinding(UserId userId, ResourceId questionId) {
        return "catalog.user-answer:" + userId.value() + ":" + questionId.value();
    }
}

