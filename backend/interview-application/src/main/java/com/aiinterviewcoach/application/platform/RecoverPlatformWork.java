package com.aiinterviewcoach.application.platform;

import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.time.Instant;

/** 定时协调 retryable Job、过期 RUNNING lease 与过期 Outbox claim；不处理需 provider 回读的取消。 */
@FunctionalInterface
public interface RecoverPlatformWork {

    Result handle(Command command);

    record Command(int jobLimit, int outboxLimit, Instant now, CorrelationId correlationId) {
        public Command {
            DomainPreconditions.require(jobLimit > 0 && jobLimit <= 500,
                    DomainErrorCode.INVALID_ARGUMENT, "job recovery limit must be between 1 and 500");
            DomainPreconditions.require(outboxLimit > 0 && outboxLimit <= 500,
                    DomainErrorCode.INVALID_ARGUMENT, "outbox recovery limit must be between 1 and 500");
            DomainPreconditions.requireNonNull(now, "recoveryTime");
            DomainPreconditions.requireNonNull(correlationId, "recoveryCorrelationId");
        }
    }

    record Result(int retryableJobsRequeued, int expiredJobLeasesReclaimed, int outboxClaimsReclaimed) {
        public Result {
            DomainPreconditions.require(retryableJobsRequeued >= 0 && expiredJobLeasesReclaimed >= 0
                            && outboxClaimsReclaimed >= 0,
                    DomainErrorCode.INVALID_ARGUMENT, "recovery counts must not be negative");
        }
    }
}
