package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.domain.interview.InterviewMode;
import com.ruoyi.interview.domain.interview.SessionCommandType;
import com.ruoyi.interview.domain.interview.SessionState;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * REST/SSE gap/刷新后的权威恢复快照。跨域状态只通过最小投影进入本对象，浏览器本地状态不能覆盖它。
 */
public record InterviewSessionSnapshot(
        ResourceId id,
        ResourceId planId,
        int planVersionNo,
        String planContentHash,
        InterviewMode mode,
        SessionState state,
        List<InterviewTurnView> turns,
        int lastStableTurnSequence,
        List<ResourceId> pendingJobIds,
        Reservation reservation,
        Report report,
        Optional<VoiceSummary> voiceSummary,
        List<SessionCommandType> allowedCommands,
        Optional<String> streamCursor,
        Optional<Instant> recoveryExpiresAt,
        Optional<String> failureCode,
        Optional<Instant> startedAt,
        Optional<Instant> completedAt,
        AggregateVersion version
) {
    public InterviewSessionSnapshot {
        DomainPreconditions.requireNonNull(id, "sessionId");
        DomainPreconditions.requireNonNull(planId, "planId");
        DomainPreconditions.require(planVersionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "plan version number must be positive");
        planContentHash = DomainPreconditions.requireText(planContentHash, "planContentHash");
        DomainPreconditions.require(planContentHash.matches("[a-f0-9]{64}"),
                DomainErrorCode.INVALID_ARGUMENT, "plan content hash must be lowercase SHA-256");
        DomainPreconditions.requireNonNull(mode, "interviewMode");
        DomainPreconditions.requireNonNull(state, "sessionState");
        turns = List.copyOf(DomainPreconditions.requireNonNull(turns, "turns"));
        DomainPreconditions.require(lastStableTurnSequence >= 0
                        && lastStableTurnSequence <= turns.size(),
                DomainErrorCode.INVALID_ARGUMENT, "last stable turn sequence is invalid");
        for (int index = 0; index < turns.size(); index++) {
            DomainPreconditions.require(turns.get(index).sequence() == index + 1,
                    DomainErrorCode.INVALID_STATE, "snapshot turn sequences must be contiguous from one");
        }
        pendingJobIds = DomainPreconditions.requireNonNull(pendingJobIds, "pendingJobIds").stream()
                .map(jobId -> DomainPreconditions.requireNonNull(jobId, "pendingJobId"))
                .distinct()
                .sorted(Comparator.comparing(ResourceId::value))
                .toList();
        reservation = DomainPreconditions.requireNonNull(reservation, "reservation");
        report = DomainPreconditions.requireNonNull(report, "report");
        voiceSummary = voiceSummary == null ? Optional.empty() : voiceSummary;
        allowedCommands = DomainPreconditions.requireNonNull(allowedCommands, "allowedCommands").stream()
                .map(command -> DomainPreconditions.requireNonNull(command, "allowedCommand"))
                .distinct()
                .sorted(Comparator.comparing(SessionCommandType::name))
                .toList();
        streamCursor = streamCursor == null ? Optional.empty() : streamCursor;
        streamCursor.ifPresent(cursor -> {
            DomainPreconditions.requireText(cursor, "streamCursor");
            DomainPreconditions.require(cursor.length() <= 512, DomainErrorCode.INVALID_ARGUMENT,
                    "stream cursor exceeds maximum length");
        });
        recoveryExpiresAt = recoveryExpiresAt == null ? Optional.empty() : recoveryExpiresAt;
        failureCode = failureCode == null ? Optional.empty() : failureCode;
        startedAt = startedAt == null ? Optional.empty() : startedAt;
        completedAt = completedAt == null ? Optional.empty() : completedAt;
        failureCode.ifPresent(code -> {
            DomainPreconditions.require(code.matches("[A-Z][A-Z0-9_]{0,95}"),
                    DomainErrorCode.INVALID_ARGUMENT, "failure code is not a stable reason code");
        });
        DomainPreconditions.requireNonNull(version, "sessionVersion");
    }

    public record Reservation(ResourceId id, ReservationState state) {
        public Reservation {
            DomainPreconditions.requireNonNull(id, "reservationId");
            DomainPreconditions.requireNonNull(state, "reservationState");
        }
    }

    public enum ReservationState {
        RESERVED,
        SETTLED,
        RELEASED,
        EXPIRED
    }

    public record Report(Optional<ResourceId> id, ReportState state) {
        public Report {
            id = id == null ? Optional.empty() : id;
            DomainPreconditions.requireNonNull(state, "reportState");
            boolean published = state == ReportState.READY || state == ReportState.PARTIAL;
            DomainPreconditions.require(published == id.isPresent(), DomainErrorCode.INVALID_STATE,
                    "report ID is inconsistent with report state");
        }
    }

    public enum ReportState {
        NOT_REQUESTED,
        PENDING,
        RUNNING,
        READY,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public record VoiceSummary(
            ResourceId executionId,
            VoiceState state,
            Optional<ResourceId> transcriptId,
            Optional<ResourceId> audioArtifactId
    ) {
        public VoiceSummary {
            DomainPreconditions.requireNonNull(executionId, "voiceExecutionId");
            DomainPreconditions.requireNonNull(state, "voiceState");
            transcriptId = transcriptId == null ? Optional.empty() : transcriptId;
            audioArtifactId = audioArtifactId == null ? Optional.empty() : audioArtifactId;
        }
    }

    public enum VoiceState {
        IDLE,
        LISTENING,
        TRANSCRIBING,
        CONFIRMING,
        THINKING,
        SPEAKING,
        DEGRADED,
        CANCELLED
    }
}
