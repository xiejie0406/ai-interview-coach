-- 用户答案覆盖：题目保持公共，答案按 tenant/user/question 隔离。
create table catalog.user_question_answer (
    tenant_id varchar(128) not null,
    user_id varchar(128) not null,
    question_id varchar(128) not null,
    answer_key_id varchar(255) not null,
    answer_algorithm varchar(64) not null,
    answer_nonce bytea not null,
    answer_ciphertext bytea not null,
    answer_aad_hash varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (tenant_id, user_id, question_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, question_id) references catalog.question(tenant_id, question_id),
    check (updated_at >= created_at)
);

create index user_question_answer_lookup_idx
    on catalog.user_question_answer (tenant_id, user_id, question_id);
