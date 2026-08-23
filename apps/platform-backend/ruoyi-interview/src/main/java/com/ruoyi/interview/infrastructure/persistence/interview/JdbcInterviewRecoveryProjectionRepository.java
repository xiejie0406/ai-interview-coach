package com.ruoyi.interview.infrastructure.persistence.interview;

import com.ruoyi.interview.application.interview.InterviewSessionSnapshot;
import com.ruoyi.interview.application.interview.port.InterviewRecoveryProjectionPort;
import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.domain.platform.DurableStreamType;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Interview consumer-owned 恢复投影。跨域表只读取最小稳定事实；streamCursor 只来自
 * PostgreSQL durable stream head，禁止把 Outbox eventId 或内存 sequence 冒充恢复游标。
 */
@Repository
@Transactional(readOnly = true)
public class JdbcInterviewRecoveryProjectionRepository implements InterviewRecoveryProjectionPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final DurableStreamPort streams;

    public JdbcInterviewRecoveryProjectionRepository(
            NamedParameterJdbcTemplate jdbc,
            DurableStreamPort streams
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.streams = java.util.Objects.requireNonNull(streams);
    }

    @Override
    public Optional<Projection> find(
            TenantId tenantId,
            UserId ownerId,
            ResourceId sessionId,
            ResourceId reservationId
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value()).addValue("ownerId", ownerId.value())
                .addValue("ownerRuoyiId", Long.parseLong(ownerId.value()))
                .addValue("sessionId", sessionId.value()).addValue("reservationId", reservationId.value());
        List<InterviewSessionSnapshot.Reservation> reservations = jdbc.query("""
                select r.reservation_id, r.state
                  from interview.session s
                  join billing.usage_reservation r
                    on r.tenant_id = s.tenant_id
                   and r.reservation_id = s.usage_reservation_id
                   and r.user_id = cast(s.ruoyi_user_id as varchar)
                 where s.tenant_id = :tenantId and s.ruoyi_user_id = :ownerRuoyiId
                   and s.session_id = :sessionId
                   and s.usage_reservation_id = :reservationId
                """, parameters, (row, rowNum) -> new InterviewSessionSnapshot.Reservation(
                ResourceId.of(row.getString("reservation_id")),
                InterviewSessionSnapshot.ReservationState.valueOf(row.getString("state"))));
        if (reservations.isEmpty()) {
            return Optional.empty();
        }
        if (reservations.size() != 1) {
            throw new DataIntegrityViolationException("interview reservation projection is not unique");
        }
        return Optional.of(new Projection(reservations.getFirst(), report(parameters), voice(parameters),
                pendingJobs(parameters), streams.currentCursor(
                tenantId, DurableStreamType.INTERVIEW, sessionId)));
    }

    private InterviewSessionSnapshot.Report report(MapSqlParameterSource parameters) {
        List<InterviewSessionSnapshot.Report> reports = jdbc.query("""
                select report_id, status
                  from evaluation.report
                 where tenant_id = :tenantId and user_id = :ownerId
                   and source_interview_id = :sessionId
                """, parameters, (row, rowNum) -> reportFromPublishedRoot(row));
        if (reports.size() > 1) {
            throw new DataIntegrityViolationException("interview report projection is not unique");
        }
        if (!reports.isEmpty()) {
            return reports.getFirst();
        }

        List<String> evaluationStates = jdbc.query("""
                select status
                  from evaluation.evaluation_run
                 where tenant_id = :tenantId and user_id = :ownerId
                   and source_interview_id = :sessionId
                 order by requested_at desc, evaluation_id desc
                 limit 1
                """, parameters, (row, rowNum) -> row.getString("status"));
        if (evaluationStates.isEmpty()) {
            return new InterviewSessionSnapshot.Report(Optional.empty(),
                    InterviewSessionSnapshot.ReportState.NOT_REQUESTED);
        }
        InterviewSessionSnapshot.ReportState state = switch (evaluationStates.getFirst()) {
            case "PENDING" -> InterviewSessionSnapshot.ReportState.PENDING;
            case "RUNNING", "FAILED_RETRYABLE" -> InterviewSessionSnapshot.ReportState.RUNNING;
            case "FAILED_FINAL" -> InterviewSessionSnapshot.ReportState.FAILED;
            case "CANCELLED" -> InterviewSessionSnapshot.ReportState.CANCELLED;
            case "SUCCEEDED" -> throw new DataIntegrityViolationException(
                    "succeeded evaluation has no report projection");
            default -> throw new DataIntegrityViolationException("unknown evaluation status in recovery projection");
        };
        return new InterviewSessionSnapshot.Report(Optional.empty(), state);
    }

    private static InterviewSessionSnapshot.Report reportFromPublishedRoot(ResultSet row) throws SQLException {
        InterviewSessionSnapshot.ReportState state = InterviewSessionSnapshot.ReportState.valueOf(
                row.getString("status"));
        Optional<ResourceId> reportId = state == InterviewSessionSnapshot.ReportState.READY
                || state == InterviewSessionSnapshot.ReportState.PARTIAL
                ? Optional.of(ResourceId.of(row.getString("report_id"))) : Optional.empty();
        return new InterviewSessionSnapshot.Report(reportId, state);
    }

    private Optional<InterviewSessionSnapshot.VoiceSummary> voice(MapSqlParameterSource parameters) {
        return jdbc.query("""
                select v.execution_id, v.state, v.transcript_id,
                       case when v.state = 'SPEAKING'
                            then coalesce(v.output_artifact_id, v.input_artifact_id)
                            else coalesce(v.input_artifact_id, v.output_artifact_id)
                       end as audio_artifact_id
                  from voice.turn_execution v
                  join interview.turn t
                    on t.tenant_id = v.tenant_id
                   and t.session_id = v.session_id and t.turn_id = v.turn_id
                 where v.tenant_id = :tenantId and v.session_id = :sessionId
                 order by t.sequence_no desc, v.execution_id desc
                 limit 1
                """, parameters, (row, rowNum) -> new InterviewSessionSnapshot.VoiceSummary(
                ResourceId.of(row.getString("execution_id")),
                InterviewSessionSnapshot.VoiceState.valueOf(row.getString("state")),
                optionalResource(row.getString("transcript_id")),
                optionalResource(row.getString("audio_artifact_id")))).stream().findFirst();
    }

    private List<ResourceId> pendingJobs(MapSqlParameterSource parameters) {
        return jdbc.query("""
                select distinct j.job_id
                  from platform.job j
                 where j.tenant_id = :tenantId
                   and j.state in ('PENDING','RUNNING','FAILED_RETRYABLE','CANCEL_REQUESTED')
                   and (
                       (j.job_type = 'INTERVIEW_STEP' and j.business_operation_id = :sessionId)
                       or (
                           j.job_type = 'EVALUATION_PIPELINE'
                           and exists (
                               select 1 from evaluation.evaluation_run e
                                where e.tenant_id = j.tenant_id
                                  and e.evaluation_id = j.business_operation_id
                                  and e.user_id = :ownerId
                                  and e.source_interview_id = :sessionId
                           )
                       )
                   )
                 order by j.job_id
                """, parameters, (row, rowNum) -> ResourceId.of(row.getString("job_id")));
    }

    private static Optional<ResourceId> optionalResource(String value) {
        return value == null ? Optional.empty() : Optional.of(ResourceId.of(value));
    }
}

