package com.aiinterviewcoach.adapters.outbound.identity;

import com.aiinterviewcoach.application.identity.port.IdentityChannelPort;
import com.aiinterviewcoach.domain.platform.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** EMAIL_PASSWORD 渠道候选；标识使用 keyed HMAC，credential 只保存 PasswordEncoder 输出。 */
@Transactional(readOnly = true)
public class JdbcPasswordIdentityChannelAdapter implements IdentityChannelPort {

    public static final String CHANNEL = "EMAIL_PASSWORD";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}$");

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final HmacSha256 identifiers;

    public JdbcPasswordIdentityChannelAdapter(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            byte[] identifierHmacKey
    ) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.passwordEncoder = java.util.Objects.requireNonNull(passwordEncoder);
        this.identifiers = new HmacSha256(identifierHmacKey);
    }

    @Override
    public RegistrationDecision verifyRegistration(RegistrationProof proof) {
        java.util.Objects.requireNonNull(proof, "registrationProof");
        String normalized = normalize(proof.normalizedIdentifier());
        String hash = identifiers.hash("identity-identifier-v1", normalized);
        if (!CHANNEL.equals(proof.channel())) {
            return new RegistrationDecision(false, hash, Optional.of("IDENTITY_CHANNEL_UNSUPPORTED"));
        }
        if (!EMAIL.matcher(normalized).matches() || proof.proofValue().length() < 8
                || proof.proofValue().length() > 256) {
            return new RegistrationDecision(false, hash, Optional.of("REGISTRATION_PROOF_REJECTED"));
        }
        return new RegistrationDecision(true, hash, Optional.empty());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bindRegistrationCredential(RegistrationProof proof, UserId userId) {
        RegistrationDecision verified = verifyRegistration(proof);
        if (!verified.verified()) {
            throw new DataIntegrityViolationException("registration credential was not verified");
        }
        String passwordHash = passwordEncoder.encode(proof.proofValue());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("identifierHash", verified.normalizedIdentifierHash())
                .addValue("userId", userId.value())
                .addValue("passwordHash", passwordHash);
        int inserted = jdbc.update("""
                insert into identity.password_credential (
                    identifier_hash, user_id, password_hash, credential_version, created_at
                ) values (:identifierHash, :userId, :passwordHash, 1, current_timestamp)
                on conflict do nothing
                """, parameters);
        if (inserted == 1) {
            return;
        }
        List<StoredCredential> stored = jdbc.query("""
                select user_id, password_hash from identity.password_credential
                 where identifier_hash = :identifierHash
                """, parameters, (row, rowNum) -> new StoredCredential(
                row.getString("user_id"), row.getString("password_hash")));
        if (stored.size() != 1 || !userId.value().equals(stored.getFirst().userId())
                || !passwordEncoder.matches(proof.proofValue(), stored.getFirst().passwordHash())) {
            throw new DataIntegrityViolationException("registration credential binding conflict");
        }
    }

    @Override
    public AuthenticationDecision authenticate(AuthenticationProof proof) {
        java.util.Objects.requireNonNull(proof, "authenticationProof");
        String normalized = normalize(proof.normalizedIdentifier());
        if (!CHANNEL.equals(proof.channel()) || !EMAIL.matcher(normalized).matches()) {
            return rejected();
        }
        String hash = identifiers.hash("identity-identifier-v1", normalized);
        List<StoredCredential> credentials = jdbc.query("""
                select user_id, password_hash from identity.password_credential
                 where identifier_hash = :identifierHash
                """, Map.of("identifierHash", hash), (row, rowNum) -> new StoredCredential(
                row.getString("user_id"), row.getString("password_hash")));
        if (credentials.size() != 1
                || !passwordEncoder.matches(proof.proofValue(), credentials.getFirst().passwordHash())) {
            return rejected();
        }
        return new AuthenticationDecision(true,
                Optional.of(UserId.of(credentials.getFirst().userId())), Optional.empty());
    }

    private static AuthenticationDecision rejected() {
        return new AuthenticationDecision(false, Optional.empty(), Optional.of("AUTHENTICATION_REJECTED"));
    }

    private static String normalize(String identifier) {
        return identifier.strip().toLowerCase(Locale.ROOT);
    }

    private record StoredCredential(String userId, String passwordHash) {
        @Override
        public String toString() {
            return "StoredCredential[userId=<redacted>, passwordHash=<redacted>]";
        }
    }
}
