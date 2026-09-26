-- 会话反馈的幂等生成租约与加密产物。只新增表，不变更历史迁移/原始回答。
create table evaluation.interview_feedback (
    tenant_id varchar(160) not null,
    user_id varchar(160) not null,
    interview_id varchar(160) not null,
    status varchar(16) not null check (status in ('RUNNING','COMPLETED','FAILED')),
    attempt_id varchar(64) not null,
    lease_until timestamptz not null,
    key_id varchar(160), algorithm varchar(64), nonce bytea, ciphertext bytea, aad_hash varchar(128),
    primary key (tenant_id, interview_id),
    foreign key (tenant_id, interview_id) references interview.session(tenant_id, session_id),
    check (status <> 'COMPLETED' or (key_id is not null and algorithm is not null
        and nonce is not null and ciphertext is not null and aad_hash is not null))
);
