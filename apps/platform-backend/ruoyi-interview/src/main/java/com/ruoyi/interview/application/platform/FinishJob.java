package com.ruoyi.interview.application.platform;

import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.JobFailure;
import com.ruoyi.interview.domain.platform.JobState;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Instant;
import java.util.Optional;

/** 业务 effect durable 后才可成功；实现必须用 execution 对权威 Job 重验 lease/attempt/version。 */
@FunctionalInterface
public interface FinishJob {

    Result handle(Command command);

    record Command(
            JobExecutionContext execution,
            boolean succeeded,
            Optional<JobFailure> failure,
            Instant finishedAt
    ) {
        public Command {
            DomainPreconditions.requireNonNull(execution, "jobExecutionContext");
            failure = failure == null ? Optional.empty() : failure;
            DomainPreconditions.require(succeeded != failure.isPresent(),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "job success and failure detail are inconsistent");
            DomainPreconditions.requireNonNull(finishedAt, "finishedAt");
            execution.requireLocallyUnexpired(finishedAt);
        }
    }

    record Result(ResourceId jobId, JobState state, AggregateVersion version) {
        public Result {
            DomainPreconditions.requireNonNull(jobId, "jobId");
            DomainPreconditions.requireNonNull(state, "jobState");
            DomainPreconditions.requireNonNull(version, "jobVersion");
        }
    }
}
