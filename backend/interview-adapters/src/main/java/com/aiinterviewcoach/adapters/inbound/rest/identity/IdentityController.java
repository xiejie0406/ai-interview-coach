package com.aiinterviewcoach.adapters.inbound.rest.identity;

import com.aiinterviewcoach.adapters.inbound.rest.RestInboundAdapter;
import com.aiinterviewcoach.adapters.inbound.rest.common.HttpVersionPreconditions;
import com.aiinterviewcoach.adapters.inbound.rest.common.RequestContextFactory;
import com.aiinterviewcoach.application.identity.AccountView;
import com.aiinterviewcoach.application.identity.AuthenticateUser;
import com.aiinterviewcoach.application.identity.GetCurrentAccount;
import com.aiinterviewcoach.application.identity.Logout;
import com.aiinterviewcoach.application.identity.RegisterUser;
import com.aiinterviewcoach.application.identity.UpdateProfile;
import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Identity REST 协议适配；tenant/user/sessionId 全部来自服务端 Cookie 解析。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "identity-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityController implements RestInboundAdapter {

    private static final String EMAIL_PASSWORD = "EMAIL_PASSWORD";

    private final RegisterUser registerUser;
    private final AuthenticateUser authenticateUser;
    private final Logout logout;
    private final GetCurrentAccount getCurrentAccount;
    private final UpdateProfile updateProfile;
    private final IdentityChannelPort identityChannel;
    private final RequestContextFactory contexts;
    private final SessionCookiePolicy cookiePolicy;

    public IdentityController(
            RegisterUser registerUser,
            AuthenticateUser authenticateUser,
            Logout logout,
            GetCurrentAccount getCurrentAccount,
            UpdateProfile updateProfile,
            IdentityChannelPort identityChannel,
            RequestContextFactory contexts,
            SessionCookiePolicy cookiePolicy
    ) {
        this.registerUser = java.util.Objects.requireNonNull(registerUser);
        this.authenticateUser = java.util.Objects.requireNonNull(authenticateUser);
        this.logout = java.util.Objects.requireNonNull(logout);
        this.getCurrentAccount = java.util.Objects.requireNonNull(getCurrentAccount);
        this.updateProfile = java.util.Objects.requireNonNull(updateProfile);
        this.identityChannel = java.util.Objects.requireNonNull(identityChannel);
        this.contexts = java.util.Objects.requireNonNull(contexts);
        this.cookiePolicy = java.util.Objects.requireNonNull(cookiePolicy);
    }

    @PostMapping(path = "/auth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountResponse> register(
            @Valid @RequestBody RegisterRequest body,
            HttpServletRequest request
    ) {
        var proof = new IdentityChannelPort.RegistrationProof(
                EMAIL_PASSWORD, body.email(), body.password());
        // 该 hash 由身份渠道的 keyed-HMAC 产生；原始 email 不进入幂等 scope。
        String anonymousScope = identityChannel.verifyRegistration(proof).normalizedIdentifierHash();
        var context = contexts.anonymousOperation(request, anonymousScope, true);
        RegisterUser.Result result = registerUser.handle(new RegisterUser.Command(
                proof, body.displayName(), body.locale(), validTimeZone(body.timeZone()),
                acceptedPolicies(body.acceptedPolicyVersions()), context));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(AccountResponse.from(result.account()));
    }

    @PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request
    ) {
        var proof = new IdentityChannelPort.AuthenticationProof(
                EMAIL_PASSWORD, body.email(), body.password());
        var scopeProof = new IdentityChannelPort.RegistrationProof(
                EMAIL_PASSWORD, body.email(), body.password());
        String anonymousScope = identityChannel.verifyRegistration(scopeProof).normalizedIdentifierHash();
        var context = contexts.anonymousOperation(request, anonymousScope, false);
        AuthenticateUser.Result result = authenticateUser.handle(new AuthenticateUser.Command(proof, context));
        ResponseCookie cookie = issuedCookie(result.issuedSession(), context.requestedAt());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var resolved = contexts.resolvedPrincipal(request);
        var context = contexts.operation(request);
        if (!resolved.principalRef().equals(context.requirePrincipal())) {
            throw new IllegalArgumentException("session principal changed during logout");
        }
        logout.handle(new Logout.Command(resolved.sessionId(), context));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> currentAccount(HttpServletRequest request) {
        AccountView account = getCurrentAccount.handle(new GetCurrentAccount.Query(contexts.query(request)));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .eTag(HttpVersionPreconditions.etag(account.version()))
                .body(AccountResponse.from(account));
    }

    @PatchMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountResponse> updateProfile(
            @RequestHeader(name = "If-Match") String ifMatch,
            @Valid @RequestBody ProfilePatch body,
            HttpServletRequest request
    ) {
        if (body.isEmpty()) {
            throw new IllegalArgumentException("profile patch must contain at least one field");
        }
        var context = contexts.operation(request);
        AccountView account = updateProfile.handle(new UpdateProfile.Command(
                Optional.ofNullable(body.displayName()), Optional.ofNullable(body.javaExperienceYears()),
                Optional.ofNullable(body.targetRole()), Optional.ofNullable(body.targetLevel()),
                HttpVersionPreconditions.requireIfMatch(ifMatch), context));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .eTag(HttpVersionPreconditions.etag(account.version()))
                .body(AccountResponse.from(account));
    }

    private ResponseCookie issuedCookie(
            com.aiinterviewcoach.application.identity.port.WebSessionPort.IssuedSession session,
            java.time.Instant issuedAt
    ) {
        Duration maxAge = Duration.between(issuedAt, session.expiresAt());
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalStateException("issued session is already expired");
        }
        return ResponseCookie.from(RequestContextFactory.SESSION_COOKIE, session.opaqueSessionToken())
                .httpOnly(true)
                .secure(cookiePolicy.secure())
                .sameSite(cookiePolicy.sameSite())
                .path(cookiePolicy.path())
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearedCookie() {
        return ResponseCookie.from(RequestContextFactory.SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(cookiePolicy.secure())
                .sameSite(cookiePolicy.sameSite())
                .path(cookiePolicy.path())
                .maxAge(Duration.ZERO)
                .build();
    }

    private static Map<ConsentPurpose, String> acceptedPolicies(Map<String, String> requested) {
        EnumMap<ConsentPurpose, String> result = new EnumMap<>(ConsentPurpose.class);
        requested.forEach((key, value) -> {
            final ConsentPurpose purpose;
            try {
                purpose = ConsentPurpose.valueOf(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unsupported registration policy purpose", exception);
            }
            if (value == null || value.isBlank() || value.length() > 64) {
                throw new IllegalArgumentException("registration policy version is invalid");
            }
            result.put(purpose, value.strip());
        });
        return Map.copyOf(result);
    }

    private static String validTimeZone(String value) {
        try {
            return ZoneId.of(value).getId();
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("timeZone is invalid", exception);
        }
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 256) String password,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Pattern(regexp = "zh-CN") String locale,
            @NotBlank @Size(max = 64) String timeZone,
            @NotNull @Size(min = 2, max = 2) Map<String, String> acceptedPolicyVersions
    ) {
        @Override
        public String toString() {
            return "RegisterRequest[email=<redacted>, password=<redacted>, displayName=<redacted>, locale="
                    + locale + ", timeZone=" + timeZone + ", acceptedPolicyPurposes="
                    + (acceptedPolicyVersions == null ? "<absent>" : acceptedPolicyVersions.keySet()) + "]";
        }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 256) String password
    ) {
        @Override
        public String toString() {
            return "LoginRequest[email=<redacted>, password=<redacted>]";
        }
    }

    public record ProfilePatch(
            @Size(min = 1, max = 80) String displayName,
            @Min(0) @Max(50) Integer javaExperienceYears,
            @Pattern(regexp = "JAVA_BACKEND|AI_APPLICATION|AGENT_ENGINEER") String targetRole,
            @Pattern(regexp = "JUNIOR|MID|SENIOR") String targetLevel
    ) {
        boolean isEmpty() {
            return displayName == null && javaExperienceYears == null
                    && targetRole == null && targetLevel == null;
        }

        @Override
        public String toString() {
            return "ProfilePatch[displayName=" + (displayName == null ? "<absent>" : "<redacted>")
                    + ", javaExperienceYears=" + javaExperienceYears + ", targetRole=" + targetRole
                    + ", targetLevel=" + targetLevel + "]";
        }
    }

    public record AccountResponse(
            String userId,
            String tenantId,
            String displayName,
            String locale,
            String timeZone,
            String accountStatus,
            long version
    ) {
        static AccountResponse from(AccountView view) {
            return new AccountResponse(view.userId().value(), view.tenantId().value(), view.displayName(),
                    view.locale(), view.timeZone(), view.accountStatus().name(), view.version().value());
        }

        @Override
        public String toString() {
            return "AccountResponse[userId=" + userId + ", tenantId=" + tenantId
                    + ", displayName=<redacted>, locale=" + locale + ", timeZone=" + timeZone
                    + ", accountStatus=" + accountStatus + ", version=" + version + "]";
        }
    }
}
