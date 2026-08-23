package com.aiinterviewcoach.adapters.outbound.persistence.identity;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.EncryptedEnvelope;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.LengthPrefixedCodec;
import com.aiinterviewcoach.adapters.outbound.persistence.shared.SensitiveEnvelopeCipher;
import com.aiinterviewcoach.application.identity.ProfileContentDigest;
import com.aiinterviewcoach.application.identity.port.IdentityRepository;
import com.aiinterviewcoach.application.identity.internal.RegistrationIdentifierPort;
import com.aiinterviewcoach.domain.identity.Membership;
import com.aiinterviewcoach.domain.identity.MembershipRole;
import com.aiinterviewcoach.domain.identity.MembershipStatus;
import com.aiinterviewcoach.domain.identity.ProfileVersion;
import com.aiinterviewcoach.domain.identity.Tenant;
import com.aiinterviewcoach.domain.identity.TenantStatus;
import com.aiinterviewcoach.domain.identity.TenantType;
import com.aiinterviewcoach.domain.identity.UserAccount;
import com.aiinterviewcoach.domain.identity.UserAccountStatus;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class JdbcIdentityRepository implements IdentityRepository, RegistrationIdentifierPort {

    private static final TenantId GLOBAL_IDENTITY_SCOPE = TenantId.of("GLOBAL_IDENTITY");

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;

    public JdbcIdentityRepository(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public Optional<UserAccount> findUser(UserId userId) {
        return jdbc.query("""
                select * from identity.user_account where user_id = :userId
                """, Map.of("userId", userId.value()), userMapper()).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findUserByIdentifierHash(String normalizedIdentifierHash) {
        return jdbc.query("""
                select u.*
                  from identity.user_identifier i
                  join identity.user_account u on u.user_id = i.user_id
                 where i.identifier_hash = :identifierHash
                """, Map.of("identifierHash", normalizedIdentifierHash), userMapper()).stream().findFirst();
    }

    @Override
    public Optional<UserId> findOwner(String normalizedIdentifierHash) {
        return jdbc.query("""
                select user_id from identity.user_identifier
                 where identifier_hash = :identifierHash
                """, Map.of("identifierHash", normalizedIdentifierHash),
                (row, rowNum) -> UserId.of(row.getString("user_id"))).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bind(String normalizedIdentifierHash, UserId userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("identifierHash", normalizedIdentifierHash)
                .addValue("userId", userId.value());
        int inserted = jdbc.update("""
                insert into identity.user_identifier (
                    identifier_hash, user_id, channel, verified_at
                ) values (
                    :identifierHash, :userId, 'NORMALIZED_IDENTIFIER_HASH', current_timestamp
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            String owner = jdbc.queryForObject("""
                    select user_id from identity.user_identifier
                     where identifier_hash = :identifierHash
                    """, parameters, String.class);
            if (!userId.value().equals(owner)) {
                throw new DomainException(DomainErrorCode.INVALID_STATE,
                        "registration identifier is already bound");
            }
        }
    }

    @Override
    public Optional<Tenant> findTenant(TenantId tenantId) {
        return jdbc.query("""
                select * from identity.tenant where tenant_id = :tenantId
                """, Map.of("tenantId", tenantId.value()), tenantMapper()).stream().findFirst();
    }

    @Override
    public Optional<Membership> findMembership(TenantId tenantId, UserId userId) {
        return jdbc.query("""
                select * from identity.membership
                 where tenant_id = :tenantId and user_id = :userId
                """, Map.of("tenantId", tenantId.value(), "userId", userId.value()), membershipMapper())
                .stream().findFirst();
    }

    @Override
    public List<Membership> findActiveMemberships(UserId userId) {
        // IdentityRepository explicitly allowlists this global principal-to-tenant lookup.
        return jdbc.query("""
                select * from identity.membership
                 where user_id = :userId and status = 'ACTIVE'
                 order by tenant_id, membership_id
                """, Map.of("userId", userId.value()), membershipMapper());
    }

    @Override
    public Optional<ProfileVersion> latestProfile(TenantId tenantId, UserId userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("userId", userId.value());
        return jdbc.query("""
                select p.*
                  from identity.profile_version p
                  join identity.tenant t
                    on t.tenant_id = p.tenant_id
                   and t.tenant_type = 'PERSONAL' and t.owner_user_id = p.user_id
                  join identity.membership m
                    on m.tenant_id = p.tenant_id and m.user_id = p.user_id
                   and m.role = 'OWNER' and m.status = 'ACTIVE'
                 where p.tenant_id = :tenantId and p.user_id = :userId
                 order by p.version_no desc, p.effective_at desc, p.profile_version_id desc
                 limit 1
                """, parameters, profileMapper()).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveUser(UserAccount account) {
        String binding = "identity.user:" + account.id().value() + ":display";
        EncryptedEnvelope display = cipher.encrypt(GLOBAL_IDENTITY_SCOPE, binding, account.displayName());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", account.id().value())
                .addValue("locale", account.locale())
                .addValue("timeZone", account.timeZone())
                .addValue("status", account.status().name())
                .addValue("version", account.version().value());
        JdbcPersistenceSupport.addEnvelope(parameters, "display", display);
        try {
            JdbcPersistenceSupport.versionedUpsert(jdbc, """
                    insert into identity.user_account (
                        user_id, locale, time_zone, display_key_id, display_algorithm, display_nonce,
                        display_ciphertext, display_aad_hash, status, aggregate_version
                    ) values (
                        :userId, :locale, :timeZone, :displayKeyId, :displayAlgorithm, :displayNonce,
                        :displayCiphertext, :displayAadHash, :status, :version
                    ) on conflict do nothing
                    """, """
                    update identity.user_account
                       set locale = :locale, time_zone = :timeZone,
                           display_key_id = :displayKeyId, display_algorithm = :displayAlgorithm,
                           display_nonce = :displayNonce, display_ciphertext = :displayCiphertext,
                           display_aad_hash = :displayAadHash, status = :status,
                           aggregate_version = :version
                     where user_id = :userId and aggregate_version = :expectedVersion
                    """, parameters, account.version().value(), "identity user " + account.id().value());
        } catch (OptimisticLockingFailureException exception) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                    "identity account version changed during persistence");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTenant(Tenant tenant) {
        String binding = "identity.tenant:" + tenant.id().value() + ":display";
        EncryptedEnvelope display = cipher.encrypt(tenant.id(), binding, tenant.displayName());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenant.id().value())
                .addValue("tenantType", tenant.type().name())
                .addValue("createdBy", tenant.createdByUserId().value())
                .addValue("owner", tenant.ownerUserId().map(UserId::value).orElse(null))
                .addValue("status", tenant.status().name())
                .addValue("version", tenant.version().value());
        JdbcPersistenceSupport.addEnvelope(parameters, "display", display);
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into identity.tenant (
                    tenant_id, tenant_type, created_by_user_id, owner_user_id,
                    display_key_id, display_algorithm, display_nonce, display_ciphertext,
                    display_aad_hash, status, aggregate_version
                ) values (
                    :tenantId, :tenantType, :createdBy, :owner,
                    :displayKeyId, :displayAlgorithm, :displayNonce, :displayCiphertext,
                    :displayAadHash, :status, :version
                ) on conflict do nothing
                """, """
                update identity.tenant
                   set tenant_type = :tenantType, created_by_user_id = :createdBy, owner_user_id = :owner,
                       display_key_id = :displayKeyId, display_algorithm = :displayAlgorithm,
                       display_nonce = :displayNonce, display_ciphertext = :displayCiphertext,
                       display_aad_hash = :displayAadHash, status = :status,
                       aggregate_version = :version
                 where tenant_id = :tenantId and aggregate_version = :expectedVersion
                """, parameters, tenant.version().value(), "tenant " + tenant.id().value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMembership(Membership membership) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", membership.tenantId().value())
                .addValue("membershipId", membership.id().value())
                .addValue("userId", membership.userId().value())
                .addValue("role", membership.role().name())
                .addValue("status", membership.status().name())
                .addValue("joinedAt", JdbcPersistenceSupport.writeInstant(membership.joinedAt()))
                .addValue("leftAt", JdbcPersistenceSupport.writeInstant(membership.leftAt()))
                .addValue("version", membership.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into identity.membership (
                    tenant_id, membership_id, user_id, role, status, joined_at, left_at, aggregate_version
                ) values (
                    :tenantId, :membershipId, :userId, :role, :status, :joinedAt, :leftAt, :version
                ) on conflict do nothing
                """, """
                update identity.membership
                   set role = :role, status = :status, left_at = :leftAt, aggregate_version = :version
                 where tenant_id = :tenantId and membership_id = :membershipId
                   and aggregate_version = :expectedVersion
                """, parameters, membership.version().value(),
                "membership " + membership.tenantId().value() + "/" + membership.id().value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendProfile(ProfileVersion profile, AggregateVersion accountVersion) {
        ProfileContentDigest.requireMatches(profile);
        if (profile.versionRef().versionNo() != accountVersion.value()) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                    "profile version must match the current account revision");
        }
        String encodedBody = encodeProfileBody(profile);
        EncryptedEnvelope body = cipher.encrypt(profile.tenantId(), profileBinding(profile), encodedBody);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", profile.tenantId().value())
                .addValue("profileId", profile.versionRef().resourceId().value())
                .addValue("userId", profile.userId().value())
                .addValue("versionNo", profile.versionRef().versionNo())
                .addValue("contentHash", profile.versionRef().contentHash())
                .addValue("locale", profile.locale())
                .addValue("timeZone", profile.timeZone())
                .addValue("effectiveAt", JdbcPersistenceSupport.writeInstant(profile.effectiveAt()))
                .addValue("accountVersion", accountVersion.value());
        JdbcPersistenceSupport.addEnvelope(parameters, "body", body);
        int inserted = jdbc.update("""
                insert into identity.profile_version (
                    tenant_id, profile_version_id, user_id, version_no, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash,
                    locale, time_zone, effective_at
                )
                select :tenantId, :profileId, :userId, :versionNo, :contentHash,
                       :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash,
                       :locale, :timeZone, :effectiveAt
                  from identity.tenant t
                  join identity.membership m
                    on m.tenant_id = t.tenant_id and m.user_id = t.owner_user_id
                   and m.role = 'OWNER' and m.status = 'ACTIVE'
                  join identity.user_account u on u.user_id = m.user_id
                 where t.tenant_id = :tenantId and t.tenant_type = 'PERSONAL'
                   and t.status = 'ACTIVE' and t.owner_user_id = :userId
                   and u.status = 'ACTIVE' and u.aggregate_version = :accountVersion
                   and :versionNo = :accountVersion
                   and not exists (
                       select 1 from identity.profile_version existing
                        where existing.tenant_id = :tenantId and existing.user_id = :userId
                          and existing.version_no >= :versionNo
                   )
                on conflict do nothing
                """, parameters);
        if (inserted == 1) {
            return;
        }
        verifyExistingProfile(profile, parameters);
    }

    private RowMapper<UserAccount> userMapper() {
        return (resultSet, rowNum) -> {
            UserId userId = UserId.of(resultSet.getString("user_id"));
            String display = cipher.decrypt(GLOBAL_IDENTITY_SCOPE,
                    "identity.user:" + userId.value() + ":display",
                    JdbcPersistenceSupport.readEnvelope(resultSet, "display"));
            return UserAccount.rehydrate(userId, resultSet.getString("locale"),
                    resultSet.getString("time_zone"), display,
                    UserAccountStatus.valueOf(resultSet.getString("status")),
                    new AggregateVersion(resultSet.getLong("aggregate_version")));
        };
    }

    private RowMapper<Tenant> tenantMapper() {
        return (resultSet, rowNum) -> {
            TenantId tenantId = TenantId.of(resultSet.getString("tenant_id"));
            String display = cipher.decrypt(tenantId,
                    "identity.tenant:" + tenantId.value() + ":display",
                    JdbcPersistenceSupport.readEnvelope(resultSet, "display"));
            String owner = resultSet.getString("owner_user_id");
            return Tenant.rehydrate(tenantId,
                    TenantType.valueOf(resultSet.getString("tenant_type")),
                    UserId.of(resultSet.getString("created_by_user_id")),
                    owner == null ? null : UserId.of(owner), display,
                    TenantStatus.valueOf(resultSet.getString("status")),
                    new AggregateVersion(resultSet.getLong("aggregate_version")));
        };
    }

    private RowMapper<Membership> membershipMapper() {
        return (resultSet, rowNum) -> Membership.rehydrate(
                ResourceId.of(resultSet.getString("membership_id")),
                TenantId.of(resultSet.getString("tenant_id")),
                UserId.of(resultSet.getString("user_id")),
                MembershipRole.valueOf(resultSet.getString("role")),
                JdbcPersistenceSupport.readInstant(resultSet, "joined_at"),
                MembershipStatus.valueOf(resultSet.getString("status")),
                JdbcPersistenceSupport.readNullableInstant(resultSet, "left_at"),
                new AggregateVersion(resultSet.getLong("aggregate_version")));
    }

    private RowMapper<ProfileVersion> profileMapper() {
        return (resultSet, rowNum) -> {
            TenantId tenantId = TenantId.of(resultSet.getString("tenant_id"));
            ResourceId profileId = ResourceId.of(resultSet.getString("profile_version_id"));
            String encoded = cipher.decrypt(tenantId,
                    profileBinding(tenantId, profileId),
                    JdbcPersistenceSupport.readEnvelope(resultSet, "body"));
            List<String> values = LengthPrefixedCodec.decode(encoded);
            if (values.size() != 4) {
                throw new DataIntegrityViolationException("invalid encrypted identity profile payload");
            }
            ProfileVersion profile = new ProfileVersion(
                    new ImmutableVersionRef(profileId, resultSet.getInt("version_no"),
                            resultSet.getString("content_hash")),
                    tenantId, UserId.of(resultSet.getString("user_id")),
                    values.get(0), values.get(1), values.get(2), values.get(3),
                    resultSet.getString("locale"), resultSet.getString("time_zone"),
                    JdbcPersistenceSupport.readInstant(resultSet, "effective_at"));
            if (!ProfileContentDigest.compute(profile).equals(profile.versionRef().contentHash())) {
                throw new DataIntegrityViolationException("identity profile content hash mismatch");
            }
            return profile;
        };
    }

    private void verifyExistingProfile(ProfileVersion expected, MapSqlParameterSource parameters) {
        List<ProfileVersion> existing = jdbc.query("""
                select * from identity.profile_version
                 where tenant_id = :tenantId and profile_version_id = :profileId
                   and user_id = :userId
                """, parameters, profileMapper());
        if (existing.size() != 1 || !sameImmutableProfile(existing.getFirst(), expected)) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                    "identity profile append lost its account or version CAS");
        }
        Boolean sameTimestamp = jdbc.queryForObject("""
                select effective_at = :effectiveAt
                  from identity.profile_version
                 where tenant_id = :tenantId and profile_version_id = :profileId
                   and user_id = :userId
                """, parameters, Boolean.class);
        if (!Boolean.TRUE.equals(sameTimestamp)) {
            throw new DataIntegrityViolationException("immutable identity profile timestamp collision");
        }
    }

    private static boolean sameImmutableProfile(ProfileVersion left, ProfileVersion right) {
        return left.versionRef().equals(right.versionRef())
                && left.tenantId().equals(right.tenantId())
                && left.userId().equals(right.userId())
                && left.targetRole().equals(right.targetRole())
                && left.targetLevel().equals(right.targetLevel())
                && left.javaExperienceBand().equals(right.javaExperienceBand())
                && left.aiExperienceBand().equals(right.aiExperienceBand())
                && left.locale().equals(right.locale())
                && left.timeZone().equals(right.timeZone());
    }

    private static String encodeProfileBody(ProfileVersion profile) {
        return LengthPrefixedCodec.encode(List.of(profile.targetRole(), profile.targetLevel(),
                profile.javaExperienceBand(), profile.aiExperienceBand()));
    }

    private static String profileBinding(ProfileVersion profile) {
        return profileBinding(profile.tenantId(), profile.versionRef().resourceId());
    }

    private static String profileBinding(TenantId tenantId, ResourceId profileId) {
        return "identity.profile:" + tenantId.value() + ":" + profileId.value() + ":body";
    }
}
