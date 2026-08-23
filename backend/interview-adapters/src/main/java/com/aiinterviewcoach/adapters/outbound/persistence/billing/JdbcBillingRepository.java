package com.aiinterviewcoach.adapters.outbound.persistence.billing;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport;
import com.aiinterviewcoach.application.billing.port.BillingRepository;
import com.aiinterviewcoach.domain.billing.Entitlement;
import com.aiinterviewcoach.domain.billing.EntitlementSource;
import com.aiinterviewcoach.domain.billing.EntitlementState;
import com.aiinterviewcoach.domain.billing.UsageReservation;
import com.aiinterviewcoach.domain.billing.UsageReservationState;
import com.aiinterviewcoach.domain.billing.UsageSettlement;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.IdempotencyKey;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UsageQuantity;
import com.aiinterviewcoach.domain.platform.UserId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class JdbcBillingRepository implements BillingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBillingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Entitlement> findEntitlement(TenantId tenantId, ResourceId entitlementId) {
        return jdbc.query("""
                select * from billing.entitlement
                 where tenant_id = :tenantId and entitlement_id = :entitlementId
                """, Map.of("tenantId", tenantId.value(), "entitlementId", entitlementId.value()),
                entitlementMapper()).stream().findFirst();
    }

    @Override
    public List<Entitlement> findUsableEntitlements(TenantId tenantId, UserId userId, String unit) {
        return jdbc.query("""
                select * from billing.entitlement
                 where tenant_id = :tenantId and user_id = :userId and usage_unit = :unit
                   and state = 'ACTIVE'
                 order by valid_to, entitlement_id
                """, Map.of("tenantId", tenantId.value(), "userId", userId.value(), "unit", unit),
                entitlementMapper());
    }

    @Override
    public Optional<UsageReservation> findReservation(TenantId tenantId, ResourceId reservationId) {
        return jdbc.query("""
                select * from billing.usage_reservation
                 where tenant_id = :tenantId and reservation_id = :reservationId
                """, Map.of("tenantId", tenantId.value(), "reservationId", reservationId.value()),
                reservationMapper()).stream().findFirst();
    }

    @Override
    public Optional<UsageReservation> findReservationByOperation(
            TenantId tenantId,
            ResourceId businessOperationId,
            String unit
    ) {
        return jdbc.query("""
                select * from billing.usage_reservation
                 where tenant_id = :tenantId and business_operation_id = :operationId
                   and usage_unit = :unit
                """, Map.of("tenantId", tenantId.value(), "operationId", businessOperationId.value(),
                        "unit", unit), reservationMapper()).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEntitlement(Entitlement entitlement) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", entitlement.tenantId().value())
                .addValue("entitlementId", entitlement.id().value())
                .addValue("userId", entitlement.userId().value())
                .addValue("productPlanId", entitlement.productPlanId().value())
                .addValue("source", entitlement.source().name())
                .addValue("validFrom", JdbcPersistenceSupport.writeInstant(entitlement.validFrom()))
                .addValue("validTo", JdbcPersistenceSupport.writeInstant(entitlement.validTo()))
                .addValue("unit", entitlement.limit().unit())
                .addValue("limitValue", entitlement.limit().value())
                .addValue("consumedValue", entitlement.consumed().value())
                .addValue("reservedValue", entitlement.reserved().value())
                .addValue("state", entitlement.state().name())
                .addValue("version", entitlement.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into billing.entitlement (
                    tenant_id, entitlement_id, user_id, product_plan_id, source,
                    valid_from, valid_to, usage_unit, limit_value, consumed_value,
                    reserved_value, state, aggregate_version
                ) values (
                    :tenantId, :entitlementId, :userId, :productPlanId, :source,
                    :validFrom, :validTo, :unit, :limitValue, :consumedValue,
                    :reservedValue, :state, :version
                ) on conflict do nothing
                """, """
                update billing.entitlement
                   set consumed_value = :consumedValue, reserved_value = :reservedValue,
                       state = :state, aggregate_version = :version
                 where tenant_id = :tenantId and entitlement_id = :entitlementId
                   and aggregate_version = :expectedVersion
                """, parameters, entitlement.version().value(),
                "entitlement " + entitlement.tenantId().value() + "/" + entitlement.id().value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveReservation(UsageReservation reservation) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", reservation.tenantId().value())
                .addValue("reservationId", reservation.id().value())
                .addValue("userId", reservation.userId().value())
                .addValue("entitlementId", reservation.entitlementId().value())
                .addValue("operationId", reservation.businessOperationId().value())
                .addValue("idempotencyKey", reservation.idempotencyKey().value())
                .addValue("unit", reservation.reservedQuantity().unit())
                .addValue("reservedValue", reservation.reservedQuantity().value())
                .addValue("expiresAt", JdbcPersistenceSupport.writeInstant(reservation.expiresAt()))
                .addValue("state", reservation.state().name())
                .addValue("settledValue", reservation.settledQuantity()
                        .map(UsageQuantity::value).orElse(null))
                .addValue("releaseReason", reservation.releaseReasonCode().orElse(null))
                .addValue("version", reservation.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into billing.usage_reservation (
                    tenant_id, reservation_id, user_id, entitlement_id, business_operation_id,
                    idempotency_key, usage_unit, reserved_value, expires_at, state,
                    settled_value, release_reason_code, aggregate_version
                ) values (
                    :tenantId, :reservationId, :userId, :entitlementId, :operationId,
                    :idempotencyKey, :unit, :reservedValue, :expiresAt, :state,
                    :settledValue, :releaseReason, :version
                ) on conflict do nothing
                """, """
                update billing.usage_reservation
                   set state = :state, settled_value = :settledValue,
                       release_reason_code = :releaseReason, aggregate_version = :version
                 where tenant_id = :tenantId and reservation_id = :reservationId
                   and aggregate_version = :expectedVersion
                """, parameters, reservation.version().value(),
                "usage reservation " + reservation.tenantId().value() + "/" + reservation.id().value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendSettlement(UsageSettlement settlement) {
        jdbc.update("""
                insert into billing.usage_settlement (
                    tenant_id, settlement_id, user_id, reservation_id, business_operation_id,
                    usage_unit, reserved_value, settled_value, released_value, rule_version, settled_at
                ) values (
                    :tenantId, :settlementId, :userId, :reservationId, :operationId,
                    :unit, :reservedValue, :settledValue, :releasedValue, :ruleVersion, :settledAt
                )
                """, new MapSqlParameterSource()
                .addValue("tenantId", settlement.tenantId().value())
                .addValue("settlementId", settlement.id().value())
                .addValue("userId", settlement.userId().value())
                .addValue("reservationId", settlement.reservationId().value())
                .addValue("operationId", settlement.businessOperationId().value())
                .addValue("unit", settlement.reservedQuantity().unit())
                .addValue("reservedValue", settlement.reservedQuantity().value())
                .addValue("settledValue", settlement.settledQuantity().value())
                .addValue("releasedValue", settlement.releasedQuantity().value())
                .addValue("ruleVersion", settlement.ruleVersion())
                .addValue("settledAt", JdbcPersistenceSupport.writeInstant(settlement.settledAt())));
    }

    private RowMapper<Entitlement> entitlementMapper() {
        return (resultSet, rowNum) -> {
            String unit = resultSet.getString("usage_unit");
            return Entitlement.rehydrate(
                    ResourceId.of(resultSet.getString("entitlement_id")),
                    TenantId.of(resultSet.getString("tenant_id")),
                    UserId.of(resultSet.getString("user_id")),
                    ResourceId.of(resultSet.getString("product_plan_id")),
                    EntitlementSource.valueOf(resultSet.getString("source")),
                    JdbcPersistenceSupport.readInstant(resultSet, "valid_from"),
                    JdbcPersistenceSupport.readInstant(resultSet, "valid_to"),
                    quantity(unit, resultSet.getBigDecimal("limit_value")),
                    quantity(unit, resultSet.getBigDecimal("consumed_value")),
                    quantity(unit, resultSet.getBigDecimal("reserved_value")),
                    EntitlementState.valueOf(resultSet.getString("state")),
                    new AggregateVersion(resultSet.getLong("aggregate_version")));
        };
    }

    private RowMapper<UsageReservation> reservationMapper() {
        return (resultSet, rowNum) -> {
            String unit = resultSet.getString("usage_unit");
            BigDecimal settled = resultSet.getBigDecimal("settled_value");
            String releaseReason = resultSet.getString("release_reason_code");
            return UsageReservation.rehydrate(
                    ResourceId.of(resultSet.getString("reservation_id")),
                    TenantId.of(resultSet.getString("tenant_id")),
                    UserId.of(resultSet.getString("user_id")),
                    ResourceId.of(resultSet.getString("entitlement_id")),
                    ResourceId.of(resultSet.getString("business_operation_id")),
                    new IdempotencyKey(resultSet.getString("idempotency_key")),
                    quantity(unit, resultSet.getBigDecimal("reserved_value")),
                    JdbcPersistenceSupport.readInstant(resultSet, "expires_at"),
                    UsageReservationState.valueOf(resultSet.getString("state")),
                    settled == null ? Optional.empty() : Optional.of(quantity(unit, settled)),
                    Optional.ofNullable(releaseReason),
                    new AggregateVersion(resultSet.getLong("aggregate_version")));
        };
    }

    private static UsageQuantity quantity(String unit, BigDecimal value) {
        return new UsageQuantity(unit, value);
    }
}
