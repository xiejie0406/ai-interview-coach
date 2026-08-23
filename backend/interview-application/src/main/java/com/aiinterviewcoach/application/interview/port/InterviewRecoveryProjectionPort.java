package com.aiinterviewcoach.application.interview.port;

import com.aiinterviewcoach.application.interview.InterviewSessionSnapshot;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Interview consumer-owned 的恢复读模型端口。实现必须同时使用 tenantId、ownerId 与 sessionId
 * 过滤，并从各 owner 的持久化事实生成最小投影；Report 必须按 source interview 关联查询，
 * 不能把 sessionId 伪装成 reportId。pendingJobIds 只包含 PENDING/RUNNING/FAILED_RETRYABLE/
 * CANCEL_REQUESTED 等未终结 Job，streamCursor 来自 durable event/outbox 游标；二者都不得用
 * 浏览器或进程内计数补值。
 */
public interface InterviewRecoveryProjectionPort {

    Optional<Projection> find(
            TenantId tenantId,
            UserId ownerId,
            ResourceId sessionId,
            ResourceId reservationId
    );

    record Projection(
            InterviewSessionSnapshot.Reservation reservation,
            InterviewSessionSnapshot.Report report,
            Optional<InterviewSessionSnapshot.VoiceSummary> voiceSummary,
            List<ResourceId> pendingJobIds,
            Optional<String> streamCursor
    ) {
        public Projection {
            DomainPreconditions.requireNonNull(reservation, "reservationProjection");
            DomainPreconditions.requireNonNull(report, "reportProjection");
            voiceSummary = voiceSummary == null ? Optional.empty() : voiceSummary;
            pendingJobIds = DomainPreconditions.requireNonNull(pendingJobIds, "pendingJobIds").stream()
                    .map(jobId -> DomainPreconditions.requireNonNull(jobId, "pendingJobId"))
                    .distinct()
                    .sorted(Comparator.comparing(ResourceId::value))
                    .toList();
            streamCursor = streamCursor == null ? Optional.empty() : streamCursor;
            streamCursor.ifPresent(cursor -> {
                DomainPreconditions.requireText(cursor, "streamCursor");
                DomainPreconditions.require(cursor.length() <= 512, DomainErrorCode.INVALID_ARGUMENT,
                        "stream cursor exceeds maximum length");
            });
        }
    }
}
