-- V12 身份收敛遗漏的政策 tenant 外键。保留历史政策，不回填/删除旧身份数据。
-- NOT VALID 只允许保留旧记录；所有新写入仍强制引用现行业务 tenant。
alter table governance.consent_policy_version
    drop constraint if exists consent_policy_version_tenant_id_fkey;
alter table governance.consent_policy_version
    add constraint consent_policy_business_tenant_fk foreign key (tenant_id)
        references platform.business_tenant(tenant_id) not valid;
