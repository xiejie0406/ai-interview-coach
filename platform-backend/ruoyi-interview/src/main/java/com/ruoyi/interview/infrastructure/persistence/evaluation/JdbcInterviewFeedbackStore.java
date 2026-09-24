package com.ruoyi.interview.infrastructure.persistence.evaluation;

import com.ruoyi.interview.application.evaluation.*;
import com.ruoyi.interview.domain.platform.PrincipalRef;
import com.ruoyi.interview.infrastructure.persistence.shared.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Map;

/** 会话级反馈与已有逐答案 EvaluationRun 分开存储，所有正文使用既有信封加密。 */
@Repository
public class JdbcInterviewFeedbackStore implements InterviewFeedbackStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;
    private final ObjectMapper json;
    public JdbcInterviewFeedbackStore(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher, ObjectMapper json) {
        this.jdbc = jdbc; this.cipher = cipher; this.json = json;
    }
    private Map<String, Object> key(PrincipalRef owner, String id) {
        return Map.of("tenant", owner.tenantId().value(), "user", owner.userId().value(), "id", id);
    }
    public Optional<InterviewFeedback> find(PrincipalRef owner, String id) {
        return jdbc.query("""
            select * from evaluation.interview_feedback where tenant_id=:tenant and user_id=:user
              and interview_id=:id and status='COMPLETED'
            """, key(owner, id), (row, index) -> json.readValue(cipher.decrypt(owner.tenantId(),
                "interview-feedback:" + id, new EncryptedEnvelope(row.getString("key_id"), row.getString("algorithm"),
                row.getBytes("nonce"), row.getBytes("ciphertext"), row.getString("aad_hash"))), InterviewFeedback.class))
                .stream().findFirst();
    }
    public boolean claim(PrincipalRef owner, String id, String attempt) {
        var params = new MapSqlParameterSource(key(owner, id)).addValue("attempt", attempt);
        return jdbc.update("""
            insert into evaluation.interview_feedback(tenant_id,user_id,interview_id,status,attempt_id,lease_until)
            values(:tenant,:user,:id,'RUNNING',:attempt,current_timestamp + interval '2 minutes')
            on conflict(tenant_id,interview_id) do update set status='RUNNING',attempt_id=:attempt,
                lease_until=current_timestamp + interval '2 minutes'
            where interview_feedback.user_id=:user and (interview_feedback.status='FAILED'
                or (interview_feedback.status='RUNNING' and interview_feedback.lease_until < current_timestamp))
            """, params) == 1;
    }
    public void publish(PrincipalRef owner, String id, String attempt, InterviewFeedback report) {
        var envelope = cipher.encrypt(owner.tenantId(), "interview-feedback:" + id, json.writeValueAsString(report));
        var params = new MapSqlParameterSource(key(owner, id)).addValue("attempt", attempt)
                .addValue("keyId", envelope.keyId()).addValue("algorithm", envelope.algorithm())
                .addValue("nonce", envelope.nonce()).addValue("ciphertext", envelope.ciphertext()).addValue("hash", envelope.aadHash());
        int rows = jdbc.update("""
            update evaluation.interview_feedback set status='COMPLETED',key_id=:keyId,algorithm=:algorithm,
                nonce=:nonce,ciphertext=:ciphertext,aad_hash=:hash
            where tenant_id=:tenant and user_id=:user and interview_id=:id and attempt_id=:attempt
                and status='RUNNING' and lease_until > current_timestamp
            """, params);
        if (rows != 1) throw new IllegalStateException("反馈生成租约失效");
    }
    public void fail(PrincipalRef owner, String id, String attempt) {
        jdbc.update("""
            update evaluation.interview_feedback set status='FAILED' where tenant_id=:tenant
                and user_id=:user and interview_id=:id and attempt_id=:attempt and status='RUNNING'
            """, new MapSqlParameterSource(key(owner, id)).addValue("attempt", attempt));
    }
}
