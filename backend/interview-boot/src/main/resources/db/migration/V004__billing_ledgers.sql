-- Billing mutable balances are protected by aggregate_version. Settlement and cost ledgers are append-only.
-- Persistence candidate only; execution remains NotRun.
comment on schema billing is 'Logical owner: billing module; physical owner: Flyway migrator current_user';

create table billing.entitlement (
    tenant_id varchar(128) not null,
    entitlement_id varchar(128) not null,
    user_id varchar(128) not null,
    product_plan_id varchar(128) not null,
    source varchar(32) not null check (source in ('TRIAL','PROMOTION','PURCHASE','ADMIN_ADJUSTMENT')),
    valid_from timestamptz not null,
    valid_to timestamptz not null,
    usage_unit varchar(64) not null,
    limit_value numeric(38,8) not null check (limit_value >= 0),
    consumed_value numeric(38,8) not null check (consumed_value >= 0),
    reserved_value numeric(38,8) not null check (reserved_value >= 0),
    state varchar(16) not null check (state in ('PENDING','ACTIVE','EXHAUSTED','EXPIRED','REVOKED')),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, entitlement_id),
    unique (tenant_id, entitlement_id, user_id, usage_unit),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    check (valid_to > valid_from),
    check (consumed_value + reserved_value <= limit_value),
    check (state not in ('EXPIRED','REVOKED') or reserved_value = 0)
);

create index entitlement_lookup_idx
    on billing.entitlement (tenant_id, user_id, usage_unit, state, valid_from, valid_to);

create table billing.usage_reservation (
    tenant_id varchar(128) not null,
    reservation_id varchar(128) not null,
    user_id varchar(128) not null,
    entitlement_id varchar(128) not null,
    business_operation_id varchar(128) not null,
    idempotency_key varchar(256) not null,
    usage_unit varchar(64) not null,
    reserved_value numeric(38,8) not null check (reserved_value > 0),
    expires_at timestamptz not null,
    state varchar(16) not null check (state in ('RESERVED','SETTLED','RELEASED','EXPIRED')),
    settled_value numeric(38,8),
    release_reason_code varchar(64),
    aggregate_version bigint not null check (aggregate_version >= 0),
    primary key (tenant_id, reservation_id),
    unique (tenant_id, business_operation_id, usage_unit),
    unique (tenant_id, user_id, idempotency_key),
    unique (tenant_id, reservation_id, user_id),
    unique (
        tenant_id, reservation_id, user_id, business_operation_id, usage_unit, reserved_value),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (tenant_id, entitlement_id, user_id, usage_unit)
        references billing.entitlement(tenant_id, entitlement_id, user_id, usage_unit),
    check (settled_value is null or (settled_value >= 0 and settled_value <= reserved_value)),
    check ((state = 'SETTLED') = (settled_value is not null)),
    check ((state = 'RELEASED') = (release_reason_code is not null)),
    check (release_reason_code is null or release_reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
);

create index reservation_expiry_idx
    on billing.usage_reservation (tenant_id, state, expires_at)
    where state = 'RESERVED';

create table billing.usage_settlement (
    tenant_id varchar(128) not null,
    settlement_id varchar(128) not null,
    user_id varchar(128) not null,
    reservation_id varchar(128) not null,
    business_operation_id varchar(128) not null,
    usage_unit varchar(64) not null,
    reserved_value numeric(38,8) not null check (reserved_value > 0),
    settled_value numeric(38,8) not null check (settled_value >= 0),
    released_value numeric(38,8) not null check (released_value >= 0),
    rule_version varchar(128) not null,
    settled_at timestamptz not null,
    primary key (tenant_id, settlement_id),
    unique (tenant_id, reservation_id),
    foreign key (tenant_id, user_id) references identity.membership(tenant_id, user_id),
    foreign key (
        tenant_id, reservation_id, user_id, business_operation_id, usage_unit, reserved_value)
        references billing.usage_reservation(
            tenant_id, reservation_id, user_id, business_operation_id, usage_unit, reserved_value),
    check (settled_value + released_value = reserved_value)
);

create table billing.provider_cost_ledger (
    tenant_id varchar(128) not null,
    cost_entry_id varchar(128) not null,
    business_operation_id varchar(128) not null,
    provider_invocation_id varchar(128) not null,
    provider_code varchar(64) not null,
    model_code varchar(128) not null,
    usage_unit varchar(64) not null,
    usage_value numeric(38,8) not null check (usage_value >= 0),
    amount numeric(38,8) not null check (amount >= 0),
    currency char(3) not null check (currency ~ '^[A-Z]{3}$'),
    pricing_rule_version varchar(128) not null,
    correlation_id varchar(128) not null,
    occurred_at timestamptz not null,
    primary key (tenant_id, cost_entry_id),
    unique (tenant_id, provider_invocation_id, usage_unit),
    foreign key (tenant_id) references identity.tenant(tenant_id)
);

comment on table billing.provider_cost_ledger is
    'Append-only provider cost facts. Current application layer has no CostLedgerPort, so no adapter is invented in this migration wave.';

create trigger usage_settlement_immutable
before update or delete on billing.usage_settlement
for each row execute function platform.reject_immutable_row_mutation();
create trigger provider_cost_ledger_immutable
before update or delete on billing.provider_cost_ledger
for each row execute function platform.reject_immutable_row_mutation();
