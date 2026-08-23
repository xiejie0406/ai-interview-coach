package com.aiinterviewcoach.application.identity.port;

import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.Optional;

/**
 * 登录/验证渠道尚未锁定时的窄端口。adapter 负责密码/验证码/OAuth 细节、限流和秘密处理。
 */
public interface IdentityChannelPort {

    RegistrationDecision verifyRegistration(RegistrationProof proof);

    /** 注册事务内把已验证 proof 绑定到新 User；实现不得把原始 proof 或 credential 写入日志。 */
    void bindRegistrationCredential(RegistrationProof proof, UserId userId);

    AuthenticationDecision authenticate(AuthenticationProof proof);

    record RegistrationProof(String channel, String normalizedIdentifier, String proofValue) {
        public RegistrationProof {
            channel = DomainPreconditions.requireText(channel, "identityChannel");
            normalizedIdentifier = DomainPreconditions.requireText(normalizedIdentifier, "normalizedIdentifier");
            proofValue = DomainPreconditions.requireText(proofValue, "registrationProof");
        }

        @Override
        public String toString() {
            return "RegistrationProof[channel=" + channel
                    + ", normalizedIdentifier=<redacted>, proofValue=<redacted>]";
        }
    }

    record RegistrationDecision(boolean verified, String normalizedIdentifierHash, Optional<String> rejectionCode) {
        public RegistrationDecision {
            normalizedIdentifierHash = DomainPreconditions.requireText(
                    normalizedIdentifierHash, "normalizedIdentifierHash");
            rejectionCode = rejectionCode == null ? Optional.empty() : rejectionCode;
            DomainPreconditions.require(verified != rejectionCode.isPresent(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "verified registration and rejection code are inconsistent");
        }

        @Override
        public String toString() {
            return "RegistrationDecision[verified=" + verified
                    + ", normalizedIdentifierHash=<redacted>, rejectionCode=" + rejectionCode + "]";
        }
    }

    record AuthenticationProof(String channel, String normalizedIdentifier, String proofValue) {
        public AuthenticationProof {
            channel = DomainPreconditions.requireText(channel, "identityChannel");
            normalizedIdentifier = DomainPreconditions.requireText(normalizedIdentifier, "normalizedIdentifier");
            proofValue = DomainPreconditions.requireText(proofValue, "authenticationProof");
        }

        @Override
        public String toString() {
            return "AuthenticationProof[channel=" + channel
                    + ", normalizedIdentifier=<redacted>, proofValue=<redacted>]";
        }
    }

    record AuthenticationDecision(boolean authenticated, Optional<UserId> userId, Optional<String> rejectionCode) {
        public AuthenticationDecision {
            userId = userId == null ? Optional.empty() : userId;
            rejectionCode = rejectionCode == null ? Optional.empty() : rejectionCode;
            DomainPreconditions.require(authenticated == userId.isPresent() && authenticated != rejectionCode.isPresent(),
                    com.aiinterviewcoach.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "authentication decision is inconsistent");
        }
    }
}
