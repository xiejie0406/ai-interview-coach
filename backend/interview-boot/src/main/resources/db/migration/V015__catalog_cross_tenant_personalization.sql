-- 公共题库与登录用户可属于不同租户；分别保存题库作用域和用户作用域。
drop table catalog.user_question_answer;

create table catalog.user_question_answer (
    catalog_tenant_id varchar(128) not null,
    owner_tenant_id varchar(128) not null,
    user_id varchar(128) not null,
    question_id varchar(128) not null,
    answer_key_id varchar(255) not null,
    answer_algorithm varchar(64) not null,
    answer_nonce bytea not null,
    answer_ciphertext bytea not null,
    answer_aad_hash varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (catalog_tenant_id, owner_tenant_id, user_id, question_id),
    foreign key (owner_tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (catalog_tenant_id, question_id) references catalog.question(tenant_id, question_id),
    check (updated_at >= created_at)
);

create index user_question_answer_lookup_v2_idx
    on catalog.user_question_answer (catalog_tenant_id, owner_tenant_id, user_id, question_id);

create table catalog.public_question_creator (
    catalog_tenant_id varchar(128) not null,
    question_id varchar(128) not null,
    creator_tenant_id varchar(128) not null,
    creator_user_id varchar(128) not null,
    created_at timestamptz not null,
    primary key (catalog_tenant_id, question_id),
    foreign key (catalog_tenant_id, question_id) references catalog.question(tenant_id, question_id),
    foreign key (creator_tenant_id, creator_user_id) references identity.membership(tenant_id, user_id)
);
