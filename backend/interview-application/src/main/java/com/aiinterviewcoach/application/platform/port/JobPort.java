package com.aiinterviewcoach.application.platform.port;

import com.aiinterviewcoach.domain.platform.Job;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Job 的 owner persistence port；claim 必须由 adapter 以原子数据库操作实现。 */
public interface JobPort {

    Optional<Job> find(TenantId tenantId, ResourceId jobId);

    Optional<Job> findByBusinessOperation(TenantId tenantId, String jobType, ResourceId businessOperationId);

    void save(Job job);

    /** 仅供批准的系统 Worker 扫描；返回 Job 后的每次读写必须重新携带该 Job 的 tenantId。 */
    List<Job> findClaimable(String jobType, Instant availableBefore, int limit);

    /** 锁定已到 retryAt 的 FAILED_RETRYABLE；调用方必须在同一事务内 makePending 并保存。 */
    List<Job> findRetryable(Instant availableBefore, int limit);

    /** 锁定 lease 已过期的 RUNNING；CANCEL_REQUESTED 必须走带回读证据的独立恢复用例。 */
    List<Job> findExpiredRunningLeases(Instant expiredBefore, int limit);
}
