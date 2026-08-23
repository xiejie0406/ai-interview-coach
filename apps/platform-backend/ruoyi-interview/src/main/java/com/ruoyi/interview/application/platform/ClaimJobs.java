package com.ruoyi.interview.application.platform;

import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Worker 通过此用例 claim，不能在 adapter 中先查后改造成双执行。 */
@FunctionalInterface
public interface ClaimJobs {

    List<ClaimedJob> handle(Command command);

    record Command(
            String jobType,
            String workerId,
            Duration leaseDuration,
            int limit,
            Instant now,
            CorrelationId correlationId
    ) {
        public Command {
            jobType = DomainPreconditions.requireText(jobType, "jobType");
            workerId = DomainPreconditions.requireText(workerId, "workerId");
            DomainPreconditions.requireNonNull(leaseDuration, "leaseDuration");
            DomainPreconditions.require(!leaseDuration.isNegative() && !leaseDuration.isZero(),
                    DomainErrorCode.INVALID_ARGUMENT, "lease duration must be positive");
            DomainPreconditions.require(limit > 0 && limit <= 100, DomainErrorCode.INVALID_ARGUMENT,
                    "claim limit must be between 1 and 100");
            DomainPreconditions.requireNonNull(now, "now");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }
    }

    record ClaimedJob(
            JobExecutionContext execution,
            String jobType,
            ResourceId businessOperationId,
            Map<String, String> payloadReferences
    ) {
        public ClaimedJob {
            DomainPreconditions.requireNonNull(execution, "jobExecutionContext");
            jobType = DomainPreconditions.requireText(jobType, "jobType");
            DomainPreconditions.requireNonNull(businessOperationId, "businessOperationId");
            payloadReferences = Map.copyOf(payloadReferences == null ? Map.of() : payloadReferences);
        }
    }
}
