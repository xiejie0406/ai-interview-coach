package com.aiinterviewcoach.domain.identity;

import com.aiinterviewcoach.domain.platform.AggregateRoot;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Locale;
import java.util.Map;

/** 不持有明文密码、验证码或登录凭据；认证渠道由 adapter 端口提供。 */
public final class UserAccount extends AggregateRoot {

    private final UserId id;
    private final String locale;
    private final String timeZone;
    private String displayName;
    private UserAccountStatus status;
    private AggregateVersion version;

    private UserAccount(
            UserId id,
            String locale,
            String timeZone,
            String displayName,
            UserAccountStatus status,
            AggregateVersion version
    ) {
        this.id = DomainPreconditions.requireNonNull(id, "userId");
        this.locale = DomainPreconditions.requireText(locale, "locale").toLowerCase(Locale.ROOT);
        this.timeZone = DomainPreconditions.requireText(timeZone, "timeZone");
        this.displayName = DomainPreconditions.requireText(displayName, "displayName");
        this.status = DomainPreconditions.requireNonNull(status, "accountStatus");
        this.version = DomainPreconditions.requireNonNull(version, "accountVersion");
    }

    public static UserAccount register(
            UserId id,
            String locale,
            String timeZone,
            String displayName,
            TenantId eventTenantId,
            EventContext context
    ) {
        UserAccount account = new UserAccount(id, locale, timeZone, displayName,
                UserAccountStatus.PENDING, AggregateVersion.initial());
        account.recordEvent("identity.user.registered", eventTenantId, account.resourceId(),
                account.version, context, Map.of("userId", id.value().toString()));
        return account;
    }

    /** 从持久化重建时使用；不产生新的领域事件。 */
    public static UserAccount rehydrate(
            UserId id,
            String locale,
            String timeZone,
            String displayName,
            UserAccountStatus status,
            AggregateVersion version
    ) {
        return new UserAccount(id, locale, timeZone, displayName, status, version);
    }

    public UserId id() {
        return id;
    }

    public String locale() {
        return locale;
    }

    public String timeZone() {
        return timeZone;
    }

    public String displayName() {
        return displayName;
    }

    public UserAccountStatus status() {
        return status;
    }

    public AggregateVersion version() {
        return version;
    }

    public boolean canAuthenticate() {
        return status == UserAccountStatus.ACTIVE;
    }

    public void requireActive() {
        DomainPreconditions.require(status == UserAccountStatus.ACTIVE, DomainErrorCode.ACCOUNT_NOT_ACTIVE,
                "user account is not active");
    }

    public void activate(TenantId eventTenantId, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == UserAccountStatus.PENDING || status == UserAccountStatus.LOCKED,
                DomainErrorCode.INVALID_STATE, "account cannot be activated from current state");
        transition(UserAccountStatus.ACTIVE, "identity.user.activated", eventTenantId, context);
    }

    public void lock(TenantId eventTenantId, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == UserAccountStatus.ACTIVE, DomainErrorCode.INVALID_STATE,
                "only an active account can be locked");
        transition(UserAccountStatus.LOCKED, "identity.user.locked", eventTenantId, context);
    }

    public void disable(TenantId eventTenantId, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == UserAccountStatus.ACTIVE || status == UserAccountStatus.LOCKED,
                DomainErrorCode.INVALID_STATE, "account cannot be disabled from current state");
        transition(UserAccountStatus.DISABLED, "identity.user.disabled", eventTenantId, context);
    }

    public void beginClosing(TenantId eventTenantId, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status != UserAccountStatus.CLOSED && status != UserAccountStatus.CLOSING,
                DomainErrorCode.INVALID_STATE, "account cannot begin closing from current state");
        transition(UserAccountStatus.CLOSING, "identity.user.closing_started", eventTenantId, context);
    }

    public void close(TenantId eventTenantId, AggregateVersion expectedVersion, EventContext context) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status == UserAccountStatus.CLOSING, DomainErrorCode.INVALID_STATE,
                "account must be closing before it can be closed");
        transition(UserAccountStatus.CLOSED, "identity.user.closed", eventTenantId, context);
    }

    public void rename(
            String displayName,
            TenantId eventTenantId,
            AggregateVersion expectedVersion,
            EventContext context
    ) {
        expectedVersion(expectedVersion);
        DomainPreconditions.require(status != UserAccountStatus.CLOSED, DomainErrorCode.INVALID_STATE,
                "closed account cannot be renamed");
        this.displayName = DomainPreconditions.requireText(displayName, "displayName");
        version = version.next();
        recordEvent("identity.user.profile_changed", eventTenantId, resourceId(), version, context, Map.of());
    }

    private void transition(
            UserAccountStatus next,
            String eventType,
            TenantId eventTenantId,
            EventContext context
    ) {
        status = next;
        version = version.next();
        recordEvent(eventType, eventTenantId, resourceId(), version, context, Map.of("status", status.name()));
    }

    private void expectedVersion(AggregateVersion expectedVersion) {
        version.requireMatches(expectedVersion);
    }

    private ResourceId resourceId() {
        return ResourceId.of(id.value());
    }
}
