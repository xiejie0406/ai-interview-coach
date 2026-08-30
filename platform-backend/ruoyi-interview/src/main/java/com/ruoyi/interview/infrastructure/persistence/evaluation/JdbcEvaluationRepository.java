package com.ruoyi.interview.infrastructure.persistence.evaluation;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.LengthPrefixedCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.PersistenceJsonCodec;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.evaluation.EvaluationRepository;
import com.ruoyi.interview.domain.evaluation.EvaluationPolicySnapshot;
import com.ruoyi.interview.domain.evaluation.EvaluationReport;
import com.ruoyi.interview.domain.evaluation.EvaluationRun;
import com.ruoyi.interview.domain.evaluation.EvaluationSourceRef;
import com.ruoyi.interview.domain.evaluation.EvaluationStage;
import com.ruoyi.interview.domain.evaluation.EvaluationStatus;
import com.ruoyi.interview.domain.evaluation.EvidenceBundle;
import com.ruoyi.interview.domain.evaluation.EvidenceItem;
import com.ruoyi.interview.domain.evaluation.EvidenceType;
import com.ruoyi.interview.domain.evaluation.FeedbackNote;
import com.ruoyi.interview.domain.evaluation.FeedbackType;
import com.ruoyi.interview.domain.evaluation.ReportComposition;
import com.ruoyi.interview.domain.evaluation.ReportSection;
import com.ruoyi.interview.domain.evaluation.ReportStatus;
import com.ruoyi.interview.domain.evaluation.ReportVersion;
import com.ruoyi.interview.domain.evaluation.RubricConfidence;
import com.ruoyi.interview.domain.evaluation.RubricDimensionScore;
import com.ruoyi.interview.domain.evaluation.RubricJudgement;
import com.ruoyi.interview.domain.evaluation.RubricScore;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.PromptSchemaPin;
import com.ruoyi.interview.domain.platform.ProviderPolicySnapshot;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Evaluation/Report schema 的 JDBC owner；所有聚合写入必须加入 application 事务。 */
@Repository
@Transactional(readOnly = true)
public class JdbcEvaluationRepository implements EvaluationRepository {

    private static final String REPORT_COMPOSITION_FORMAT = "report-composition-v1";

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;
    private final PersistenceJsonCodec json;

    public JdbcEvaluationRepository(
            NamedParameterJdbcTemplate jdbc,
            SensitiveEnvelopeCipher cipher,
            PersistenceJsonCodec json
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.cipher = java.util.Objects.requireNonNull(cipher);
        this.json = java.util.Objects.requireNonNull(json);
    }

    @Override
    public Optional<EvaluationRun> findRun(TenantId tenantId, ResourceId evaluationId) {
        return jdbc.query("""
                select * from evaluation.evaluation_run
                 where tenant_id = :tenantId and evaluation_id = :evaluationId
                """, Map.of("tenantId", tenantId.value(), "evaluationId", evaluationId.value()),
                (row, rowNum) -> mapRun(row)).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveRun(EvaluationRun run) {
        run.evidenceBundle().ifPresent(bundle -> appendEvidenceBundle(run.tenantId(), bundle));
        run.rubricJudgement().ifPresent(score -> appendRubricJudgement(run.tenantId(), score));

        EvaluationSourceRef source = run.sourceRef();
        EvaluationPolicySnapshot policy = run.policySnapshot();
        ProviderPolicySnapshot provider = policy.providerPolicy();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", run.tenantId().value())
                .addValue("evaluationId", run.id().value())
                .addValue("userId", run.userId().value())
                .addValue("answerVersionId", source.answerVersionId().value())
                .addValue("answerHash", source.answerHash())
                .addValue("sourceInterviewId", source.interviewId().value())
                .addValue("sourcePlanId", source.planId().value())
                .addValue("sourcePlanVersionNo", Math.toIntExact(source.planVersionNo()))
                .addValue("transcriptVersionId", source.confirmedTranscriptVersionId()
                        .map(ResourceId::value).orElse(null))
                .addValue("configVersionId", policy.configVersionId())
                .addValue("evidencePromptKey", policy.evidencePin().promptKey())
                .addValue("evidencePromptVersion", policy.evidencePin().promptVersion())
                .addValue("evidenceSchemaKey", policy.evidencePin().schemaKey())
                .addValue("evidenceSchemaVersion", policy.evidencePin().schemaVersion())
                .addValue("judgePromptKey", policy.judgePin().promptKey())
                .addValue("judgePromptVersion", policy.judgePin().promptVersion())
                .addValue("judgeSchemaKey", policy.judgePin().schemaKey())
                .addValue("judgeSchemaVersion", policy.judgePin().schemaVersion())
                .addValue("reportPromptKey", policy.reportPin().promptKey())
                .addValue("reportPromptVersion", policy.reportPin().promptVersion())
                .addValue("reportSchemaKey", policy.reportPin().schemaKey())
                .addValue("reportSchemaVersion", policy.reportPin().schemaVersion())
                .addValue("routePlanId", provider.routePlanId())
                .addValue("primaryProvider", provider.primaryProvider())
                .addValue("primaryModel", provider.primaryModel())
                .addValue("fallbackProvider", provider.fallbackProvider())
                .addValue("fallbackModel", provider.fallbackModel())
                .addValue("safetyFlags", json.write(provider.safetyFlags()))
                .addValue("rubricVersionId", policy.rubricVersionId())
                .addValue("status", run.status().name())
                .addValue("stage", run.stage().name())
                .addValue("evidenceBundleId", run.evidenceBundle()
                        .map(EvidenceBundle::bundleId).map(ResourceId::value).orElse(null))
                .addValue("judgementId", run.rubricJudgement()
                        .map(RubricScore::judgementId).map(ResourceId::value).orElse(null))
                .addValue("evaluationVersionId", run.evaluationVersionId()
                        .map(ResourceId::value).orElse(null))
                .addValue("reportId", run.reportId().map(ResourceId::value).orElse(null))
                .addValue("failureCode", run.failureCode().orElse(null))
                .addValue("requestedAt", run.requestedAt())
                .addValue("completedAt", run.completedAt().orElse(null))
                .addValue("version", run.version().value());

        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into evaluation.evaluation_run (
                    tenant_id, evaluation_id, user_id,
                    answer_version_id, answer_hash, source_interview_id,
                    source_plan_id, source_plan_version_no, confirmed_transcript_version_id,
                    config_version_id,
                    evidence_prompt_key, evidence_prompt_version,
                    evidence_schema_key, evidence_schema_version,
                    judge_prompt_key, judge_prompt_version, judge_schema_key, judge_schema_version,
                    report_prompt_key, report_prompt_version, report_schema_key, report_schema_version,
                    provider_route_plan_id, primary_provider, primary_model,
                    fallback_provider, fallback_model, safety_flags, rubric_version_id,
                    status, stage, evidence_bundle_id, judgement_id, evaluation_version_id,
                    report_id, failure_code, requested_at, completed_at, aggregate_version
                ) values (
                    :tenantId, :evaluationId, :userId,
                    :answerVersionId, :answerHash, :sourceInterviewId,
                    :sourcePlanId, :sourcePlanVersionNo, :transcriptVersionId,
                    :configVersionId,
                    :evidencePromptKey, :evidencePromptVersion,
                    :evidenceSchemaKey, :evidenceSchemaVersion,
                    :judgePromptKey, :judgePromptVersion, :judgeSchemaKey, :judgeSchemaVersion,
                    :reportPromptKey, :reportPromptVersion, :reportSchemaKey, :reportSchemaVersion,
                    :routePlanId, :primaryProvider, :primaryModel,
                    :fallbackProvider, :fallbackModel, cast(:safetyFlags as jsonb), :rubricVersionId,
                    :status, :stage, :evidenceBundleId, :judgementId, :evaluationVersionId,
                    :reportId, :failureCode, :requestedAt, :completedAt, :version
                ) on conflict do nothing
                """, """
                update evaluation.evaluation_run
                   set status = :status, stage = :stage,
                       evidence_bundle_id = :evidenceBundleId, judgement_id = :judgementId,
                       evaluation_version_id = :evaluationVersionId, report_id = :reportId,
                       failure_code = :failureCode, completed_at = :completedAt,
                       aggregate_version = :version
                 where tenant_id = :tenantId and evaluation_id = :evaluationId
                   and aggregate_version = :expectedVersion
                """, parameters, run.version().value(),
                "evaluation run " + run.tenantId().value() + "/" + run.id().value());
    }

    @Override
    public Optional<EvaluationReport> findReport(TenantId tenantId, ResourceId reportId) {
        return queryReport("report_id", tenantId, reportId);
    }

    @Override
    public Optional<EvaluationReport> findReportByEvaluation(TenantId tenantId, ResourceId evaluationId) {
        return queryReport("evaluation_id", tenantId, evaluationId);
    }

    @Override
    public Optional<EvaluationReport> findReportByInterview(TenantId tenantId, ResourceId interviewId) {
        return queryReport("source_interview_id", tenantId, interviewId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveReport(EvaluationReport report) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", report.tenantId().value())
                .addValue("reportId", report.id().value())
                .addValue("userId", report.userId().value())
                .addValue("evaluationId", report.evaluationId().value())
                .addValue("sourceInterviewId", report.sourceInterviewId().value())
                .addValue("status", report.status().name())
                .addValue("reportVersionId", report.reportVersion()
                        .map(ReportVersion::id).map(ResourceId::value).orElse(null))
                .addValue("failureCode", report.failureCode().orElse(null))
                .addValue("version", report.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into evaluation.report (
                    tenant_id, report_id, user_id, evaluation_id, source_interview_id,
                    status, current_report_version_id, failure_code, aggregate_version
                ) values (
                    :tenantId, :reportId, :userId, :evaluationId, :sourceInterviewId,
                    :status, :reportVersionId, :failureCode, :version
                ) on conflict do nothing
                """, """
                update evaluation.report
                   set status = :status, current_report_version_id = :reportVersionId,
                       failure_code = :failureCode, aggregate_version = :version
                 where tenant_id = :tenantId and report_id = :reportId
                   and aggregate_version = :expectedVersion
                """, parameters, report.version().value(),
                "evaluation report " + report.tenantId().value() + "/" + report.id().value());

        report.reportVersion().ifPresent(version -> appendReportVersion(report, version));
        report.feedbackNotes().forEach(note -> appendFeedback(report, note));
    }

    private Optional<EvaluationReport> queryReport(String column, TenantId tenantId, ResourceId value) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("value", value.value());
        return jdbc.query("select * from evaluation.report where tenant_id = :tenantId and "
                        + column + " = :value", parameters,
                (row, rowNum) -> mapReport(row)).stream().findFirst();
    }

    private EvaluationRun mapRun(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        EvaluationSourceRef source = new EvaluationSourceRef(
                ResourceId.of(row.getString("answer_version_id")), row.getString("answer_hash"),
                ResourceId.of(row.getString("source_interview_id")),
                ResourceId.of(row.getString("source_plan_id")), row.getLong("source_plan_version_no"),
                optionalResource(row.getString("confirmed_transcript_version_id")));
        PromptSchemaPin evidencePin = pin(row, "evidence");
        PromptSchemaPin judgePin = pin(row, "judge");
        PromptSchemaPin reportPin = pin(row, "report");
        ProviderPolicySnapshot provider = new ProviderPolicySnapshot(
                row.getString("provider_route_plan_id"), row.getString("primary_provider"),
                row.getString("primary_model"), row.getString("fallback_provider"),
                row.getString("fallback_model"), json.readStringMap(row.getString("safety_flags")));
        EvaluationPolicySnapshot policy = new EvaluationPolicySnapshot(row.getString("config_version_id"),
                evidencePin, judgePin, reportPin, provider, row.getString("rubric_version_id"));
        Optional<EvidenceBundle> evidence = optionalResource(row.getString("evidence_bundle_id"))
                .map(id -> loadEvidenceBundle(tenantId, id));
        Optional<RubricScore> judgement = optionalResource(row.getString("judgement_id"))
                .map(id -> loadRubricJudgement(tenantId, id));
        validateRunArtifacts(source, policy, evidence, judgement);
        return EvaluationRun.rehydrate(ResourceId.of(row.getString("evaluation_id")), tenantId,
                UserId.of(row.getString("user_id")), source, policy,
                EvaluationStatus.valueOf(row.getString("status")),
                EvaluationStage.valueOf(row.getString("stage")),
                evidence.orElse(null), judgement.orElse(null),
                nullableResource(row.getString("evaluation_version_id")),
                nullableResource(row.getString("report_id")), row.getString("failure_code"),
                JdbcPersistenceSupport.readInstant(row, "requested_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "completed_at"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private EvaluationReport mapReport(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId reportId = ResourceId.of(row.getString("report_id"));
        ResourceId versionId = nullableResource(row.getString("current_report_version_id"));
        ReportVersion reportVersion = versionId == null ? null : loadReportVersion(tenantId, reportId, versionId);
        List<FeedbackNote> feedback = jdbc.query("""
                select * from evaluation.report_feedback
                 where tenant_id = :tenantId and report_id = :reportId
                 order by created_at, feedback_id
                """, Map.of("tenantId", tenantId.value(), "reportId", reportId.value()),
                (feedbackRow, rowNum) -> mapFeedback(feedbackRow));
        return EvaluationReport.rehydrate(reportId, tenantId, UserId.of(row.getString("user_id")),
                ResourceId.of(row.getString("evaluation_id")),
                ResourceId.of(row.getString("source_interview_id")),
                ReportStatus.valueOf(row.getString("status")), reportVersion,
                row.getString("failure_code"), feedback,
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private void appendEvidenceBundle(TenantId tenantId, EvidenceBundle bundle) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("bundleId", bundle.bundleId().value())
                .addValue("answerVersionId", bundle.answerVersionId().value())
                .addValue("answerHash", bundle.answerHash())
                .addValue("promptKey", bundle.schemaPin().promptKey())
                .addValue("promptVersion", bundle.schemaPin().promptVersion())
                .addValue("schemaKey", bundle.schemaPin().schemaKey())
                .addValue("schemaVersion", bundle.schemaPin().schemaVersion());
        jdbc.update("""
                insert into evaluation.evidence_bundle (
                    tenant_id, evidence_bundle_id, answer_version_id, answer_hash,
                    prompt_key, prompt_version, schema_key, schema_version
                ) values (
                    :tenantId, :bundleId, :answerVersionId, :answerHash,
                    :promptKey, :promptVersion, :schemaKey, :schemaVersion
                ) on conflict do nothing
                """, parameters);
        for (int index = 0; index < bundle.spans().size(); index++) {
            EvidenceItem item = bundle.spans().get(index);
            jdbc.update("""
                    insert into evaluation.evidence_item (
                        tenant_id, evidence_bundle_id, position, evidence_id, evidence_type,
                        start_inclusive, end_exclusive, quote_hash, reason_code
                    ) values (
                        :tenantId, :bundleId, :position, :evidenceId, :evidenceType,
                        :startInclusive, :endExclusive, :quoteHash, :reasonCode
                    ) on conflict do nothing
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId.value()).addValue("bundleId", bundle.bundleId().value())
                    .addValue("position", index + 1).addValue("evidenceId", item.evidenceId().value())
                    .addValue("evidenceType", item.type().name())
                    .addValue("startInclusive", item.startInclusive()).addValue("endExclusive", item.endExclusive())
                    .addValue("quoteHash", item.quoteHash()).addValue("reasonCode", item.reasonCode().orElse(null)));
        }
        if (!bundle.equals(loadEvidenceBundle(tenantId, bundle.bundleId()))) {
            throw collision("immutable evidence bundle", tenantId, bundle.bundleId());
        }
    }

    private EvidenceBundle loadEvidenceBundle(TenantId tenantId, ResourceId bundleId) {
        List<EvidenceBundle> values = jdbc.query("""
                select * from evaluation.evidence_bundle
                 where tenant_id = :tenantId and evidence_bundle_id = :bundleId
                """, Map.of("tenantId", tenantId.value(), "bundleId", bundleId.value()), (row, rowNum) -> {
            List<EvidenceItem> items = jdbc.query("""
                    select * from evaluation.evidence_item
                     where tenant_id = :tenantId and evidence_bundle_id = :bundleId
                     order by position
                    """, Map.of("tenantId", tenantId.value(), "bundleId", bundleId.value()),
                    (itemRow, itemIndex) -> {
                        if (itemRow.getInt("position") != itemIndex + 1) {
                            throw new DataIntegrityViolationException("evidence positions are not contiguous");
                        }
                        return new EvidenceItem(ResourceId.of(itemRow.getString("evidence_id")),
                                EvidenceType.valueOf(itemRow.getString("evidence_type")),
                                itemRow.getInt("start_inclusive"), itemRow.getInt("end_exclusive"),
                                itemRow.getString("quote_hash"),
                                Optional.ofNullable(itemRow.getString("reason_code")));
                    });
            return new EvidenceBundle(bundleId, ResourceId.of(row.getString("answer_version_id")),
                    row.getString("answer_hash"),
                    new PromptSchemaPin(row.getString("prompt_key"), row.getInt("prompt_version"),
                            row.getString("schema_key"), row.getInt("schema_version")), items);
        });
        if (values.size() != 1) {
            throw new DataIntegrityViolationException("referenced evidence bundle is missing");
        }
        return values.get(0);
    }

    private void appendRubricJudgement(TenantId tenantId, RubricScore score) {
        EncryptedEnvelope limitations = cipher.encrypt(tenantId, judgementBinding(score.judgementId()),
                LengthPrefixedCodec.encode(score.limitations()));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("judgementId", score.judgementId().value())
                .addValue("promptKey", score.schemaPin().promptKey())
                .addValue("promptVersion", score.schemaPin().promptVersion())
                .addValue("schemaKey", score.schemaPin().schemaKey())
                .addValue("schemaVersion", score.schemaPin().schemaVersion())
                .addValue("rubricVersionId", score.rubricVersionId());
        JdbcPersistenceSupport.addEnvelope(parameters, "limitations", limitations);
        jdbc.update("""
                insert into evaluation.rubric_judgement (
                    tenant_id, judgement_id, prompt_key, prompt_version, schema_key, schema_version,
                    rubric_version_id, limitations_key_id, limitations_algorithm,
                    limitations_nonce, limitations_ciphertext, limitations_aad_hash
                ) values (
                    :tenantId, :judgementId, :promptKey, :promptVersion, :schemaKey, :schemaVersion,
                    :rubricVersionId, :limitationsKeyId, :limitationsAlgorithm,
                    :limitationsNonce, :limitationsCiphertext, :limitationsAadHash
                ) on conflict do nothing
                """, parameters);
        for (int index = 0; index < score.dimensions().size(); index++) {
            RubricDimensionScore dimension = score.dimensions().get(index);
            jdbc.update("""
                    insert into evaluation.rubric_dimension (
                        tenant_id, judgement_id, position, dimension_id, judgement, confidence,
                        insufficient_evidence, reason_codes, evidence_ids
                    ) values (
                        :tenantId, :judgementId, :position, :dimensionId, :judgement, :confidence,
                        :insufficient, cast(:reasonCodes as jsonb), cast(:evidenceIds as jsonb)
                    ) on conflict do nothing
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId.value()).addValue("judgementId", score.judgementId().value())
                    .addValue("position", index + 1).addValue("dimensionId", dimension.dimensionId())
                    .addValue("judgement", dimension.judgement().name())
                    .addValue("confidence", dimension.confidence().name())
                    .addValue("insufficient", dimension.insufficientEvidence())
                    .addValue("reasonCodes", json.write(dimension.reasonCodes()))
                    .addValue("evidenceIds", json.write(dimension.evidenceIds().stream()
                            .map(ResourceId::value).toList())));
        }
        if (!score.equals(loadRubricJudgement(tenantId, score.judgementId()))) {
            throw collision("immutable rubric judgement", tenantId, score.judgementId());
        }
    }

    private RubricScore loadRubricJudgement(TenantId tenantId, ResourceId judgementId) {
        List<RubricScore> values = jdbc.query("""
                select * from evaluation.rubric_judgement
                 where tenant_id = :tenantId and judgement_id = :judgementId
                """, Map.of("tenantId", tenantId.value(), "judgementId", judgementId.value()),
                (row, rowNum) -> {
                    List<RubricDimensionScore> dimensions = jdbc.query("""
                            select * from evaluation.rubric_dimension
                             where tenant_id = :tenantId and judgement_id = :judgementId
                             order by position
                            """, Map.of("tenantId", tenantId.value(), "judgementId", judgementId.value()),
                            (dimensionRow, dimensionIndex) -> {
                                if (dimensionRow.getInt("position") != dimensionIndex + 1) {
                                    throw new DataIntegrityViolationException(
                                            "rubric dimension positions are not contiguous");
                                }
                                return new RubricDimensionScore(dimensionRow.getString("dimension_id"),
                                        RubricJudgement.valueOf(dimensionRow.getString("judgement")),
                                        RubricConfidence.valueOf(dimensionRow.getString("confidence")),
                                        dimensionRow.getBoolean("insufficient_evidence"),
                                        json.readStringList(dimensionRow.getString("reason_codes")),
                                        json.readStringList(dimensionRow.getString("evidence_ids")).stream()
                                                .map(ResourceId::of).toList());
                            });
                    String encoded = cipher.decrypt(tenantId, judgementBinding(judgementId),
                            JdbcPersistenceSupport.readEnvelope(row, "limitations"));
                    return new RubricScore(judgementId,
                            new PromptSchemaPin(row.getString("prompt_key"), row.getInt("prompt_version"),
                                    row.getString("schema_key"), row.getInt("schema_version")),
                            row.getString("rubric_version_id"), dimensions,
                            LengthPrefixedCodec.decode(encoded));
                });
        if (values.size() != 1) {
            throw new DataIntegrityViolationException("referenced rubric judgement is missing");
        }
        return values.get(0);
    }

    private void appendReportVersion(EvaluationReport report, ReportVersion version) {
        String encoded = encodeComposition(version.composition());
        String hash = sha256(encoded);
        EncryptedEnvelope envelope = cipher.encrypt(report.tenantId(), reportVersionBinding(version.id()), encoded);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", report.tenantId().value())
                .addValue("reportVersionId", version.id().value())
                .addValue("reportId", report.id().value())
                .addValue("evaluationId", report.evaluationId().value())
                .addValue("evaluationVersionId", version.evaluationVersionId().value())
                .addValue("promptKey", version.schemaPin().promptKey())
                .addValue("promptVersion", version.schemaPin().promptVersion())
                .addValue("schemaKey", version.schemaPin().schemaKey())
                .addValue("schemaVersion", version.schemaPin().schemaVersion())
                .addValue("compositionHash", hash).addValue("createdAt", version.createdAt());
        JdbcPersistenceSupport.addEnvelope(parameters, "composition", envelope);
        jdbc.update("""
                insert into evaluation.report_version (
                    tenant_id, report_version_id, report_id, evaluation_id, evaluation_version_id,
                    prompt_key, prompt_version, schema_key, schema_version, composition_hash,
                    composition_key_id, composition_algorithm, composition_nonce,
                    composition_ciphertext, composition_aad_hash, created_at
                ) values (
                    :tenantId, :reportVersionId, :reportId, :evaluationId, :evaluationVersionId,
                    :promptKey, :promptVersion, :schemaKey, :schemaVersion, :compositionHash,
                    :compositionKeyId, :compositionAlgorithm, :compositionNonce,
                    :compositionCiphertext, :compositionAadHash, :createdAt
                ) on conflict do nothing
                """, parameters);
        if (!version.equals(loadReportVersion(report.tenantId(), report.id(), version.id()))) {
            throw collision("immutable report version", report.tenantId(), version.id());
        }
    }

    private ReportVersion loadReportVersion(TenantId tenantId, ResourceId reportId, ResourceId versionId) {
        List<ReportVersion> values = jdbc.query("""
                select * from evaluation.report_version
                 where tenant_id = :tenantId and report_id = :reportId
                   and report_version_id = :reportVersionId
                """, new MapSqlParameterSource().addValue("tenantId", tenantId.value())
                .addValue("reportId", reportId.value()).addValue("reportVersionId", versionId.value()),
                (row, rowNum) -> {
                    String encoded = cipher.decrypt(tenantId, reportVersionBinding(versionId),
                            JdbcPersistenceSupport.readEnvelope(row, "composition"));
                    if (!sha256(encoded).equals(row.getString("composition_hash"))) {
                        throw new DataIntegrityViolationException("report composition hash mismatch");
                    }
                    ResourceId evaluationVersionId = ResourceId.of(row.getString("evaluation_version_id"));
                    ReportComposition composition = decodeComposition(encoded);
                    return new ReportVersion(versionId, evaluationVersionId,
                            new PromptSchemaPin(row.getString("prompt_key"), row.getInt("prompt_version"),
                                    row.getString("schema_key"), row.getInt("schema_version")),
                            composition, JdbcPersistenceSupport.readInstant(row, "created_at"));
                });
        if (values.size() != 1) {
            throw new DataIntegrityViolationException("referenced report version is missing");
        }
        return values.get(0);
    }

    private void appendFeedback(EvaluationReport report, FeedbackNote note) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", report.tenantId().value()).addValue("reportId", report.id().value())
                .addValue("evaluationId", report.evaluationId().value())
                .addValue("feedbackId", note.feedbackId().value())
                .addValue("authorUserId", note.authorUserId().value())
                .addValue("feedbackType", note.type().name())
                .addValue("commentRef", note.commentRef().orElse(null)).addValue("createdAt", note.createdAt());
        jdbc.update("""
                insert into evaluation.report_feedback (
                    tenant_id, report_id, evaluation_id, feedback_id, author_user_id,
                    feedback_type, comment_ref, created_at
                ) values (
                    :tenantId, :reportId, :evaluationId, :feedbackId, :authorUserId,
                    :feedbackType, :commentRef, :createdAt
                ) on conflict do nothing
                """, parameters);
        Boolean identical = jdbc.queryForObject("""
                select report_id = :reportId and evaluation_id = :evaluationId
                       and author_user_id = :authorUserId and feedback_type = :feedbackType
                       and comment_ref is not distinct from :commentRef and created_at = :createdAt
                  from evaluation.report_feedback
                 where tenant_id = :tenantId and feedback_id = :feedbackId
                """, parameters, Boolean.class);
        if (!Boolean.TRUE.equals(identical)) {
            throw collision("immutable report feedback", report.tenantId(), note.feedbackId());
        }
    }

    private FeedbackNote mapFeedback(ResultSet row) throws SQLException {
        return new FeedbackNote(ResourceId.of(row.getString("feedback_id")),
                UserId.of(row.getString("author_user_id")),
                FeedbackType.valueOf(row.getString("feedback_type")),
                Optional.ofNullable(row.getString("comment_ref")),
                JdbcPersistenceSupport.readInstant(row, "created_at"));
    }

    private static void validateRunArtifacts(
            EvaluationSourceRef source,
            EvaluationPolicySnapshot policy,
            Optional<EvidenceBundle> evidence,
            Optional<RubricScore> judgement
    ) {
        evidence.ifPresent(bundle -> {
            if (!bundle.answerVersionId().equals(source.answerVersionId())
                    || !bundle.answerHash().equals(source.answerHash())
                    || !bundle.schemaPin().equals(policy.evidencePin())) {
                throw new DataIntegrityViolationException("evaluation evidence does not match its pinned source");
            }
        });
        judgement.ifPresent(score -> {
            if (!score.schemaPin().equals(policy.judgePin())
                    || !score.rubricVersionId().equals(policy.rubricVersionId())) {
                throw new DataIntegrityViolationException("evaluation judgement does not match its pinned policy");
            }
            HashSet<ResourceId> evidenceIds = new HashSet<>(evidence.orElseThrow(() ->
                    new DataIntegrityViolationException("evaluation judgement has no evidence bundle"))
                    .spans().stream().map(EvidenceItem::evidenceId).toList());
            if (!evidenceIds.containsAll(score.referencedEvidenceIds())) {
                throw new DataIntegrityViolationException("evaluation judgement references unknown evidence");
            }
        });
    }

    private static PromptSchemaPin pin(ResultSet row, String prefix) throws SQLException {
        return new PromptSchemaPin(row.getString(prefix + "_prompt_key"),
                row.getInt(prefix + "_prompt_version"), row.getString(prefix + "_schema_key"),
                row.getInt(prefix + "_schema_version"));
    }

    private static String encodeComposition(ReportComposition composition) {
        List<String> values = new ArrayList<>();
        values.add(REPORT_COMPOSITION_FORMAT);
        values.add(composition.evaluationVersionId().value());
        values.add(Integer.toString(composition.sections().size()));
        for (ReportSection section : composition.sections()) {
            values.add(section.sectionId());
            values.add(section.title());
            values.add(section.body());
            appendStrings(values, section.judgementRefs());
            appendStrings(values, section.evidenceRefs().stream().map(ResourceId::value).toList());
        }
        appendStrings(values, composition.actions());
        appendStrings(values, composition.limitations());
        return LengthPrefixedCodec.encode(values);
    }

    private static ReportComposition decodeComposition(String encoded) {
        ValueCursor cursor = new ValueCursor(LengthPrefixedCodec.decode(encoded));
        if (!REPORT_COMPOSITION_FORMAT.equals(cursor.next())) {
            throw new DataIntegrityViolationException("unsupported report composition format");
        }
        ResourceId evaluationVersionId = ResourceId.of(cursor.next());
        int sectionCount = cursor.nextCount();
        List<ReportSection> sections = new ArrayList<>(sectionCount);
        for (int index = 0; index < sectionCount; index++) {
            sections.add(new ReportSection(cursor.next(), cursor.next(), cursor.next(),
                    cursor.nextList(), cursor.nextList().stream().map(ResourceId::of).toList()));
        }
        ReportComposition result = new ReportComposition(evaluationVersionId, sections,
                cursor.nextList(), cursor.nextList());
        cursor.requireEnd();
        return result;
    }

    private static void appendStrings(List<String> target, List<String> values) {
        target.add(Integer.toString(values.size()));
        target.addAll(values);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("required SHA-256 digest is unavailable", exception);
        }
    }

    private static Optional<ResourceId> optionalResource(String value) {
        return value == null ? Optional.empty() : Optional.of(ResourceId.of(value));
    }

    private static ResourceId nullableResource(String value) {
        return value == null ? null : ResourceId.of(value);
    }

    private static DataIntegrityViolationException collision(
            String resource,
            TenantId tenantId,
            ResourceId resourceId
    ) {
        return new DataIntegrityViolationException(resource + " id collision: "
                + tenantId.value() + "/" + resourceId.value());
    }

    private static String judgementBinding(ResourceId judgementId) {
        return "evaluation.rubric-judgement:" + judgementId.value() + ":limitations";
    }

    private static String reportVersionBinding(ResourceId versionId) {
        return "evaluation.report-version:" + versionId.value() + ":composition";
    }

    private static final class ValueCursor {
        private final List<String> values;
        private int offset;

        private ValueCursor(List<String> values) {
            this.values = values;
        }

        private String next() {
            if (offset >= values.size()) {
                throw new DataIntegrityViolationException("encrypted report composition is truncated");
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
                throw new DataIntegrityViolationException("encrypted report list count is invalid", exception);
            }
        }

        private List<String> nextList() {
            int count = nextCount();
            List<String> result = List.copyOf(values.subList(offset, offset + count));
            offset += count;
            return result;
        }

        private void requireEnd() {
            if (offset != values.size()) {
                throw new DataIntegrityViolationException("encrypted report composition has trailing fields");
            }
        }
    }
}

