package com.ruoyi.interview.infrastructure.persistence.interview;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.domain.interview.InterviewAnswerSource;
import com.ruoyi.interview.domain.interview.InterviewAnswerVersion;
import com.ruoyi.interview.domain.interview.InterviewMode;
import com.ruoyi.interview.domain.interview.InterviewPlan;
import com.ruoyi.interview.domain.interview.InterviewPlanReference;
import com.ruoyi.interview.domain.interview.InterviewPlanState;
import com.ruoyi.interview.domain.interview.InterviewSession;
import com.ruoyi.interview.domain.interview.InterviewTurn;
import com.ruoyi.interview.domain.interview.PlannedQuestion;
import com.ruoyi.interview.domain.interview.QuestionPrompt;
import com.ruoyi.interview.domain.interview.SessionState;
import com.ruoyi.interview.domain.interview.TurnKind;
import com.ruoyi.interview.domain.interview.TurnState;
import com.ruoyi.interview.domain.interview.UsageEstimate;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.PromptRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.SchemaRef;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.TimeBudget;
import com.ruoyi.interview.domain.platform.UsageQuantity;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class JdbcInterviewRepository implements InterviewRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;

    public JdbcInterviewRepository(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public Optional<InterviewPlan> findPlan(TenantId tenantId, ResourceId planId) {
        return jdbc.query("""
                select * from interview.plan
                 where tenant_id = :tenantId and plan_id = :planId
                """, Map.of("tenantId", tenantId.value(), "planId", planId.value()),
                (row, rowNum) -> mapPlan(row)).stream().findFirst();
    }

    @Override
    public Optional<InterviewSession> findSession(TenantId tenantId, ResourceId sessionId) {
        return jdbc.query("""
                select * from interview.session
                 where tenant_id = :tenantId and session_id = :sessionId
                """, Map.of("tenantId", tenantId.value(), "sessionId", sessionId.value()),
                (row, rowNum) -> mapSession(row)).stream().findFirst();
    }

    @Override
    public Optional<InterviewSession> findSessionByPlan(
            TenantId tenantId,
            ResourceId planId,
            int planVersionNo
    ) {
        return jdbc.query("""
                select * from interview.session
                 where tenant_id = :tenantId and plan_id = :planId
                   and plan_version_no = :planVersionNo
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("planId", planId.value())
                .addValue("planVersionNo", planVersionNo),
                (row, rowNum) -> mapSession(row)).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void savePlan(InterviewPlan plan) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", plan.tenantId().value())
                .addValue("planId", plan.id().value())
                .addValue("userId", ruoyiUserId(plan.userId()))
                .addValue("profileId", plan.profileVersion().resourceId().value())
                .addValue("profileVersionNo", plan.profileVersion().versionNo())
                .addValue("profileHash", plan.profileVersion().contentHash())
                .addValue("mode", plan.mode().name())
                .addValue("expiresAt", JdbcPersistenceSupport.writeInstant(plan.expiresAt()))
                .addValue("timeBudget", plan.totalTimeBudget().duration().toMillis())
                .addValue("followUpBudget", plan.totalFollowUpBudget())
                .addValue("estimateUnit", plan.usageEstimate().quantity().unit())
                .addValue("estimateValue", plan.usageEstimate().quantity().value())
                .addValue("estimateRule", plan.usageEstimate().ruleVersion())
                .addValue("contentHash", plan.contentHash())
                .addValue("planVersionNo", plan.planVersionNo())
                .addValue("state", plan.state().name())
                .addValue("reservationId", plan.usageReservationId().map(ResourceId::value).orElse(null))
                .addValue("version", plan.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into interview.plan (
                    tenant_id, plan_id, ruoyi_user_id,
                    profile_version_id, profile_version_no, profile_content_hash,
                    mode, expires_at, total_time_budget_millis, total_follow_up_budget,
                    estimate_unit, estimate_value, estimate_rule_version,
                    content_hash, plan_version_no, state, usage_reservation_id, aggregate_version
                ) values (
                    :tenantId, :planId, :userId,
                    :profileId, :profileVersionNo, :profileHash,
                    :mode, :expiresAt, :timeBudget, :followUpBudget,
                    :estimateUnit, :estimateValue, :estimateRule,
                    :contentHash, :planVersionNo, :state, :reservationId, :version
                ) on conflict do nothing
                """, """
                update interview.plan
                   set expires_at = :expiresAt,
                       total_time_budget_millis = :timeBudget,
                       total_follow_up_budget = :followUpBudget,
                       estimate_unit = :estimateUnit, estimate_value = :estimateValue,
                       estimate_rule_version = :estimateRule, content_hash = :contentHash,
                       plan_version_no = :planVersionNo, state = :state,
                       usage_reservation_id = :reservationId, aggregate_version = :version
                 where tenant_id = :tenantId and plan_id = :planId
                   and aggregate_version = :expectedVersion
                """, parameters, plan.version().value(),
                "interview plan " + plan.tenantId().value() + "/" + plan.id().value());

        jdbc.update("""
                delete from interview.plan_question
                 where tenant_id = :tenantId and plan_id = :planId
                """, parameters);
        plan.questions().forEach(question -> insertPlanQuestion("interview.plan_question",
                "plan_id", plan.tenantId(), plan.id(), question));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveSession(InterviewSession session) {
        InterviewPlanReference reference = session.planReference();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", session.tenantId().value())
                .addValue("sessionId", session.id().value())
                .addValue("userId", ruoyiUserId(session.userId()))
                .addValue("planId", reference.planId().value())
                .addValue("planVersionNo", reference.planVersionNo())
                .addValue("planHash", reference.contentHash())
                .addValue("reservationId", reference.usageReservationId().value())
                .addValue("followUpBudget", reference.followUpBudget())
                .addValue("mode", session.mode().name())
                .addValue("state", session.state().name())
                .addValue("lastStableSequence", session.lastStableSequence())
                .addValue("startedAt", session.startedAt().map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("pausedAt", session.pausedAt().map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("completingAt", session.completingAt().map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("completedAt", session.completedAt().map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("recoveryExpiresAt", session.recoveryExpiresAt().map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("failureCode", session.failureCode().orElse(null))
                .addValue("version", session.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into interview.session (
                    tenant_id, session_id, ruoyi_user_id, plan_id, plan_version_no, plan_content_hash,
                    usage_reservation_id, plan_follow_up_budget, mode, state, last_stable_sequence,
                    started_at, paused_at, completing_at, completed_at,
                    recovery_expires_at, failure_code, aggregate_version
                ) values (
                    :tenantId, :sessionId, :userId, :planId, :planVersionNo, :planHash,
                    :reservationId, :followUpBudget, :mode, :state, :lastStableSequence,
                    :startedAt, :pausedAt, :completingAt, :completedAt,
                    :recoveryExpiresAt, :failureCode, :version
                ) on conflict do nothing
                """, """
                update interview.session
                   set state = :state, last_stable_sequence = :lastStableSequence,
                       started_at = :startedAt, paused_at = :pausedAt,
                       completing_at = :completingAt, completed_at = :completedAt,
                       recovery_expires_at = :recoveryExpiresAt, failure_code = :failureCode,
                       aggregate_version = :version
                 where tenant_id = :tenantId and session_id = :sessionId
                   and aggregate_version = :expectedVersion
                """, parameters, session.version().value(),
                "interview session " + session.tenantId().value() + "/" + session.id().value());

        reference.questions().forEach(question -> insertImmutableSessionQuestion(
                session.tenantId(), session.id(), question));
        session.turns().forEach(turn -> saveTurn(session, turn));
    }

    private InterviewPlan mapPlan(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId planId = ResourceId.of(row.getString("plan_id"));
        List<PlannedQuestion> questions = readPlanQuestions("interview.plan_question", "plan_id",
                tenantId, planId);
        return InterviewPlan.rehydrate(planId, tenantId,
                UserId.of(Long.toString(row.getLong("ruoyi_user_id"))),
                new ImmutableVersionRef(ResourceId.of(row.getString("profile_version_id")),
                        row.getInt("profile_version_no"), row.getString("profile_content_hash")),
                InterviewMode.valueOf(row.getString("mode")),
                JdbcPersistenceSupport.readInstant(row, "expires_at"), questions,
                new TimeBudget(Duration.ofMillis(row.getLong("total_time_budget_millis"))),
                row.getInt("total_follow_up_budget"),
                new UsageEstimate(new UsageQuantity(row.getString("estimate_unit"),
                        row.getBigDecimal("estimate_value")), row.getString("estimate_rule_version")),
                row.getString("content_hash"), row.getInt("plan_version_no"),
                InterviewPlanState.valueOf(row.getString("state")),
                optionalResource(row.getString("usage_reservation_id")),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private InterviewSession mapSession(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId sessionId = ResourceId.of(row.getString("session_id"));
        List<PlannedQuestion> questions = readPlanQuestions("interview.session_plan_question",
                "session_id", tenantId, sessionId);
        InterviewPlanReference reference = new InterviewPlanReference(
                ResourceId.of(row.getString("plan_id")), row.getInt("plan_version_no"),
                row.getString("plan_content_hash"),
                ResourceId.of(row.getString("usage_reservation_id")), questions,
                row.getInt("plan_follow_up_budget"));
        List<InterviewTurn> turns = jdbc.query("""
                select * from interview.turn
                 where tenant_id = :tenantId and session_id = :sessionId
                 order by sequence_no
                """, Map.of("tenantId", tenantId.value(), "sessionId", sessionId.value()),
                (turnRow, rowNum) -> mapTurn(turnRow));
        return InterviewSession.rehydrate(sessionId, tenantId,
                UserId.of(Long.toString(row.getLong("ruoyi_user_id"))),
                reference, InterviewMode.valueOf(row.getString("mode")),
                SessionState.valueOf(row.getString("state")), turns,
                row.getInt("last_stable_sequence"),
                JdbcPersistenceSupport.readNullableInstant(row, "started_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "paused_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "completing_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "completed_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "recovery_expires_at"),
                row.getString("failure_code"), new AggregateVersion(row.getLong("aggregate_version")));
    }

    private InterviewTurn mapTurn(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId sessionId = ResourceId.of(row.getString("session_id"));
        ResourceId turnId = ResourceId.of(row.getString("turn_id"));
        PlannedQuestion plannedQuestion = new PlannedQuestion(row.getInt("planned_position"),
                immutableRef(row, "question"), immutableRef(row, "rubric"),
                row.getString("topic_code"),
                new TimeBudget(Duration.ofMillis(row.getLong("time_budget_millis"))),
                row.getInt("follow_up_budget"));
        EncryptedEnvelope promptEnvelope = JdbcPersistenceSupport.readNullableEnvelope(row, "prompt");
        QuestionPrompt prompt = promptEnvelope == null ? null : new QuestionPrompt(
                cipher.decrypt(tenantId, promptBinding(sessionId, turnId), promptEnvelope),
                row.getString("prompt_content_hash"), readPromptRef(row, "prompt"),
                readSchemaRef(row));
        List<InterviewAnswerVersion> answers = jdbc.query("""
                select * from interview.answer_version
                 where tenant_id = :tenantId and session_id = :sessionId and turn_id = :turnId
                 order by version_no desc limit 1
                """, Map.of("tenantId", tenantId.value(), "sessionId", sessionId.value(),
                        "turnId", turnId.value()), (answerRow, rowNum) -> mapAnswer(answerRow));
        return InterviewTurn.rehydrate(turnId, row.getInt("sequence_no"), plannedQuestion,
                nullableResource(row.getString("parent_turn_id")),
                TurnKind.valueOf(row.getString("kind")), TurnState.valueOf(row.getString("state")),
                prompt, answers.isEmpty() ? null : answers.getFirst(),
                JdbcPersistenceSupport.readNullableInstant(row, "committed_at"));
    }

    private InterviewAnswerVersion mapAnswer(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId answerId = ResourceId.of(row.getString("answer_version_id"));
        String text = cipher.decrypt(tenantId, answerBinding(answerId),
                JdbcPersistenceSupport.readEnvelope(row, "body"));
        return new InterviewAnswerVersion(answerId, tenantId,
                ResourceId.of(row.getString("session_id")), ResourceId.of(row.getString("turn_id")),
                row.getInt("version_no"), InterviewAnswerSource.valueOf(row.getString("source")),
                text, row.getString("content_hash"),
                optionalResource(row.getString("transcript_version_id")),
                UserId.of(Long.toString(row.getLong("confirmed_by_ruoyi_user_id"))),
                JdbcPersistenceSupport.readInstant(row, "confirmed_at"),
                optionalResource(row.getString("supersedes_id")));
    }

    private void saveTurn(InterviewSession session, InterviewTurn turn) {
        Optional<QuestionPrompt> prompt = turn.questionPrompt();
        EncryptedEnvelope envelope = prompt
                .map(value -> cipher.encrypt(session.tenantId(), promptBinding(session.id(), turn.id()),
                        value.text()))
                .orElse(null);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", session.tenantId().value())
                .addValue("sessionId", session.id().value())
                .addValue("turnId", turn.id().value())
                .addValue("sequence", turn.sequence())
                .addValue("position", turn.plannedQuestion().position())
                .addValue("questionVersionId", turn.plannedQuestion().questionVersion().resourceId().value())
                .addValue("questionVersionNo", turn.plannedQuestion().questionVersion().versionNo())
                .addValue("questionHash", turn.plannedQuestion().questionVersion().contentHash())
                .addValue("rubricVersionId", turn.plannedQuestion().rubricVersion().resourceId().value())
                .addValue("rubricVersionNo", turn.plannedQuestion().rubricVersion().versionNo())
                .addValue("rubricHash", turn.plannedQuestion().rubricVersion().contentHash())
                .addValue("topicCode", turn.plannedQuestion().topicCode())
                .addValue("timeBudget", turn.plannedQuestion().timeBudget().duration().toMillis())
                .addValue("followUpBudget", turn.plannedQuestion().followUpBudget())
                .addValue("parentTurnId", turn.parentTurnId().map(ResourceId::value).orElse(null))
                .addValue("kind", turn.kind().name())
                .addValue("state", turn.state().name())
                .addValue("promptHash", prompt.map(QuestionPrompt::contentHash).orElse(null))
                .addValue("committedAt", turn.committedAt().map(JdbcPersistenceSupport::writeInstant).orElse(null));
        JdbcPersistenceSupport.addEnvelope(parameters, "prompt", envelope);
        addPromptReference(parameters, prompt.flatMap(QuestionPrompt::promptRef));
        addSchemaReference(parameters, prompt.flatMap(QuestionPrompt::schemaRef));
        jdbc.update("""
                insert into interview.turn (
                    tenant_id, session_id, turn_id, sequence_no, planned_position,
                    question_version_id, question_version_no, question_content_hash,
                    rubric_version_id, rubric_version_no, rubric_content_hash,
                    topic_code, time_budget_millis, follow_up_budget,
                    parent_turn_id, kind, state,
                    prompt_content_hash, prompt_key_id, prompt_algorithm, prompt_nonce,
                    prompt_ciphertext, prompt_aad_hash,
                    prompt_ref_id, prompt_ref_version_no, prompt_ref_content_hash,
                    schema_ref_id, schema_ref_version_no, schema_ref_content_hash, committed_at
                ) values (
                    :tenantId, :sessionId, :turnId, :sequence, :position,
                    :questionVersionId, :questionVersionNo, :questionHash,
                    :rubricVersionId, :rubricVersionNo, :rubricHash,
                    :topicCode, :timeBudget, :followUpBudget,
                    :parentTurnId, :kind, :state,
                    :promptHash, :promptKeyId, :promptAlgorithm, :promptNonce,
                    :promptCiphertext, :promptAadHash,
                    :promptRefId, :promptRefVersionNo, :promptRefHash,
                    :schemaRefId, :schemaRefVersionNo, :schemaRefHash, :committedAt
                ) on conflict (tenant_id, session_id, turn_id) do update set
                    state = excluded.state,
                    prompt_content_hash = excluded.prompt_content_hash,
                    prompt_key_id = excluded.prompt_key_id,
                    prompt_algorithm = excluded.prompt_algorithm,
                    prompt_nonce = excluded.prompt_nonce,
                    prompt_ciphertext = excluded.prompt_ciphertext,
                    prompt_aad_hash = excluded.prompt_aad_hash,
                    prompt_ref_id = excluded.prompt_ref_id,
                    prompt_ref_version_no = excluded.prompt_ref_version_no,
                    prompt_ref_content_hash = excluded.prompt_ref_content_hash,
                    schema_ref_id = excluded.schema_ref_id,
                    schema_ref_version_no = excluded.schema_ref_version_no,
                    schema_ref_content_hash = excluded.schema_ref_content_hash,
                    committed_at = excluded.committed_at
                """, parameters);
        turn.answerVersion().ifPresent(this::appendAnswer);
    }

    private void appendAnswer(InterviewAnswerVersion answer) {
        EncryptedEnvelope body = cipher.encrypt(answer.tenantId(), answerBinding(answer.id()), answer.text());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", answer.tenantId().value())
                .addValue("answerId", answer.id().value())
                .addValue("sessionId", answer.sessionId().value())
                .addValue("turnId", answer.turnId().value())
                .addValue("versionNo", answer.versionNo())
                .addValue("source", answer.source().name())
                .addValue("contentHash", answer.contentHash())
                .addValue("transcriptVersionId", answer.transcriptVersionId()
                        .map(ResourceId::value).orElse(null))
                .addValue("confirmedBy", ruoyiUserId(answer.confirmedBy()))
                .addValue("confirmedAt", JdbcPersistenceSupport.writeInstant(answer.confirmedAt()))
                .addValue("supersedesId", answer.supersedesId().map(ResourceId::value).orElse(null));
        JdbcPersistenceSupport.addEnvelope(parameters, "body", body);
        int inserted = jdbc.update("""
                insert into interview.answer_version (
                    tenant_id, answer_version_id, session_id, turn_id, version_no, source,
                    content_hash, body_key_id, body_algorithm, body_nonce, body_ciphertext,
                    body_aad_hash, transcript_version_id, confirmed_by_ruoyi_user_id,
                    confirmed_at, supersedes_id
                ) values (
                    :tenantId, :answerId, :sessionId, :turnId, :versionNo, :source,
                    :contentHash, :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext,
                    :bodyAadHash, :transcriptVersionId, :confirmedBy, :confirmedAt, :supersedesId
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            Boolean identical = jdbc.queryForObject("""
                    select content_hash = :contentHash and session_id = :sessionId
                           and turn_id = :turnId and version_no = :versionNo
                           and source = :source
                           and transcript_version_id is not distinct from :transcriptVersionId
                           and confirmed_by_ruoyi_user_id = :confirmedBy and confirmed_at = :confirmedAt
                           and supersedes_id is not distinct from :supersedesId
                      from interview.answer_version
                     where tenant_id = :tenantId and answer_version_id = :answerId
                    """, parameters, Boolean.class);
            if (!Boolean.TRUE.equals(identical)) {
                throw new DataIntegrityViolationException("immutable interview answer id collision");
            }
        }
    }

    private List<PlannedQuestion> readPlanQuestions(
            String table,
            String ownerColumn,
            TenantId tenantId,
            ResourceId ownerId
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("ownerId", ownerId.value());
        return jdbc.query("select * from " + table + " where tenant_id = :tenantId and "
                        + ownerColumn + " = :ownerId order by position",
                parameters, (row, rowNum) -> mapPlannedQuestion(row));
    }

    private PlannedQuestion mapPlannedQuestion(ResultSet row) throws SQLException {
        return new PlannedQuestion(row.getInt("position"),
                immutableRef(row, "question"), immutableRef(row, "rubric"),
                row.getString("topic_code"),
                new TimeBudget(Duration.ofMillis(row.getLong("time_budget_millis"))),
                row.getInt("follow_up_budget"));
    }

    private void insertPlanQuestion(
            String table,
            String ownerColumn,
            TenantId tenantId,
            ResourceId ownerId,
            PlannedQuestion question
    ) {
        jdbc.update("insert into " + table + " (tenant_id, " + ownerColumn + ", position, "
                        + "question_version_id, question_version_no, question_content_hash, "
                        + "rubric_version_id, rubric_version_no, rubric_content_hash, "
                        + "topic_code, time_budget_millis, follow_up_budget) values ("
                        + ":tenantId, :ownerId, :position, :questionVersionId, :questionVersionNo, :questionHash, "
                        + ":rubricVersionId, :rubricVersionNo, :rubricHash, :topicCode, :timeBudget, :followUpBudget)",
                plannedQuestionParameters(tenantId, ownerId, question));
    }

    private void insertImmutableSessionQuestion(
            TenantId tenantId,
            ResourceId sessionId,
            PlannedQuestion question
    ) {
        MapSqlParameterSource parameters = plannedQuestionParameters(tenantId, sessionId, question);
        int inserted = jdbc.update("""
                insert into interview.session_plan_question (
                    tenant_id, session_id, position,
                    question_version_id, question_version_no, question_content_hash,
                    rubric_version_id, rubric_version_no, rubric_content_hash,
                    topic_code, time_budget_millis, follow_up_budget
                ) values (
                    :tenantId, :ownerId, :position,
                    :questionVersionId, :questionVersionNo, :questionHash,
                    :rubricVersionId, :rubricVersionNo, :rubricHash,
                    :topicCode, :timeBudget, :followUpBudget
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            Boolean identical = jdbc.queryForObject("""
                    select question_version_id = :questionVersionId
                           and question_version_no = :questionVersionNo
                           and question_content_hash = :questionHash
                           and rubric_version_id = :rubricVersionId
                           and rubric_version_no = :rubricVersionNo
                           and rubric_content_hash = :rubricHash
                           and topic_code = :topicCode
                           and time_budget_millis = :timeBudget
                           and follow_up_budget = :followUpBudget
                      from interview.session_plan_question
                     where tenant_id = :tenantId and session_id = :ownerId and position = :position
                    """, parameters, Boolean.class);
            if (!Boolean.TRUE.equals(identical)) {
                throw new DataIntegrityViolationException("confirmed session plan snapshot collision");
            }
        }
    }

    private static MapSqlParameterSource plannedQuestionParameters(
            TenantId tenantId,
            ResourceId ownerId,
            PlannedQuestion question
    ) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("ownerId", ownerId.value())
                .addValue("position", question.position())
                .addValue("questionVersionId", question.questionVersion().resourceId().value())
                .addValue("questionVersionNo", question.questionVersion().versionNo())
                .addValue("questionHash", question.questionVersion().contentHash())
                .addValue("rubricVersionId", question.rubricVersion().resourceId().value())
                .addValue("rubricVersionNo", question.rubricVersion().versionNo())
                .addValue("rubricHash", question.rubricVersion().contentHash())
                .addValue("topicCode", question.topicCode())
                .addValue("timeBudget", question.timeBudget().duration().toMillis())
                .addValue("followUpBudget", question.followUpBudget());
    }

    private static ImmutableVersionRef immutableRef(ResultSet row, String prefix) throws SQLException {
        return new ImmutableVersionRef(ResourceId.of(row.getString(prefix + "_version_id")),
                row.getInt(prefix + "_version_no"), row.getString(prefix + "_content_hash"));
    }

    private static void addPromptReference(
            MapSqlParameterSource parameters,
            Optional<PromptRef> reference
    ) {
        ImmutableVersionRef value = reference.map(PromptRef::value).orElse(null);
        parameters.addValue("promptRefId", value == null ? null : value.resourceId().value())
                .addValue("promptRefVersionNo", value == null ? null : value.versionNo())
                .addValue("promptRefHash", value == null ? null : value.contentHash());
    }

    private static void addSchemaReference(
            MapSqlParameterSource parameters,
            Optional<SchemaRef> reference
    ) {
        ImmutableVersionRef value = reference.map(SchemaRef::value).orElse(null);
        parameters.addValue("schemaRefId", value == null ? null : value.resourceId().value())
                .addValue("schemaRefVersionNo", value == null ? null : value.versionNo())
                .addValue("schemaRefHash", value == null ? null : value.contentHash());
    }

    private static Optional<PromptRef> readPromptRef(ResultSet row, String prefix) throws SQLException {
        String id = row.getString(prefix + "_ref_id");
        return id == null ? Optional.empty() : Optional.of(new PromptRef(new ImmutableVersionRef(
                ResourceId.of(id), row.getInt(prefix + "_ref_version_no"),
                row.getString(prefix + "_ref_content_hash"))));
    }

    private static Optional<SchemaRef> readSchemaRef(ResultSet row) throws SQLException {
        String id = row.getString("schema_ref_id");
        return id == null ? Optional.empty() : Optional.of(new SchemaRef(new ImmutableVersionRef(
                ResourceId.of(id), row.getInt("schema_ref_version_no"),
                row.getString("schema_ref_content_hash"))));
    }

    private static Optional<ResourceId> optionalResource(String value) {
        return value == null ? Optional.empty() : Optional.of(ResourceId.of(value));
    }

    private static long ruoyiUserId(UserId userId) {
        return Long.parseLong(userId.value());
    }

    private static ResourceId nullableResource(String value) {
        return value == null ? null : ResourceId.of(value);
    }

    private static String promptBinding(ResourceId sessionId, ResourceId turnId) {
        return "interview.session:" + sessionId.value() + ":turn:" + turnId.value() + ":prompt";
    }

    private static String answerBinding(ResourceId answerId) {
        return "interview.answer-version:" + answerId.value() + ":body";
    }
}

