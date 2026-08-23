package com.ruoyi.interview.infrastructure.job;

import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.domain.platform.Job;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 安全默认实现：不创建内存队列、线程、数据库写入或 Worker。 */
public final class UnavailableJobAdapter implements JobPort, JobAdapter {
    public static final String ADAPTER_ID = "job-unavailable";
    public static final String REASON_CODE = "JOB_PERSISTENCE_NOT_CONFIGURED";

    @Override
    public Optional<Job> find(TenantId tenantId, ResourceId jobId) {
        throw unavailable();
    }

    @Override
    public Optional<Job> findByBusinessOperation(
            TenantId tenantId,
            String jobType,
            ResourceId businessOperationId) {
        throw unavailable();
    }

    @Override
    public void save(Job job) {
        throw unavailable();
    }

    @Override
    public List<Job> findClaimable(String jobType, Instant availableBefore, int limit) {
        throw unavailable();
    }

    @Override
    public List<Job> findRetryable(Instant availableBefore, int limit) {
        throw unavailable();
    }

    @Override
    public List<Job> findExpiredRunningLeases(Instant expiredBefore, int limit) {
        throw unavailable();
    }

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String reasonCode() {
        return REASON_CODE;
    }

    private AdapterUnavailableException unavailable() {
        return new AdapterUnavailableException("job-persistence");
    }
}


