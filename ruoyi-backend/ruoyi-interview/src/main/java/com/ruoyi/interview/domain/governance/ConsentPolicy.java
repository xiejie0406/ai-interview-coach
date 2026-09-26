package com.ruoyi.interview.domain.governance;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** append-only consent history 的确定性折叠规则。 */
public final class ConsentPolicy {

    private ConsentPolicy() {
    }

    public static boolean isGranted(
            TenantId tenantId,
            UserId userId,
            ConsentPurpose purpose,
            List<ConsentRecord> history,
            Instant at
    ) {
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(userId, "userId");
        DomainPreconditions.requireNonNull(purpose, "consentPurpose");
        DomainPreconditions.requireNonNull(history, "consentHistory");
        DomainPreconditions.requireNonNull(at, "at");
        List<ConsentRecord> relevant = history.stream()
                .filter(record -> record.tenantId().equals(tenantId)
                        && record.userId().equals(userId)
                        && record.purpose() == purpose
                        && !record.effectiveAt().isAfter(at))
                .toList();
        Set<com.ruoyi.interview.domain.platform.ResourceId> superseded = new HashSet<>();
        relevant.forEach(record -> record.supersededConsentId().ifPresent(superseded::add));
        List<ConsentRecord> terminalFacts = relevant.stream()
                .filter(record -> !superseded.contains(record.id()))
                .toList();
        // 独立冲突分支不能用随机 ID 打破平局；安全默认拒绝并交由治理修复事实链。
        return terminalFacts.size() == 1 && terminalFacts.get(0).isGranted();
    }

    public static void requireGranted(
            TenantId tenantId,
            UserId userId,
            ConsentPurpose purpose,
            List<ConsentRecord> history,
            Instant at
    ) {
        DomainPreconditions.require(isGranted(tenantId, userId, purpose, history, at),
                DomainErrorCode.CONSENT_REQUIRED, "required consent has not been granted or was revoked");
    }
}
