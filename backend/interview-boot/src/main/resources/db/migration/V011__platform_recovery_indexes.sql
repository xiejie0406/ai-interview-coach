-- Recovery scanner indexes for Job retry/lease and Outbox expired claim candidates.
-- Candidate only; this migration has not been executed.

create index job_retryable_idx
    on platform.job (available_at, tenant_id, job_id)
    where state = 'FAILED_RETRYABLE';

create index job_expired_running_lease_idx
    on platform.job (lease_expires_at, tenant_id, job_id)
    where state = 'RUNNING';

create index outbox_expired_claim_idx
    on platform.outbox_delivery (claim_expires_at, tenant_id, event_id)
    where state = 'CLAIMED';
