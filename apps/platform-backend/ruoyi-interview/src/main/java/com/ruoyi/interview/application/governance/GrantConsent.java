package com.ruoyi.interview.application.governance;

import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.governance.ConsentAction;
import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;

/** GRANT/REVOKE 都追加新事实，不覆盖已有 ConsentRecord。 */
@FunctionalInterface
public interface GrantConsent {

    Result handle(Command command);

    record Command(
            ConsentPurpose purpose,
            ConsentAction action,
            ImmutableVersionRef policyVersion,
            String source,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(purpose, "consentPurpose");
            DomainPreconditions.requireNonNull(action, "consentAction");
            DomainPreconditions.requireNonNull(policyVersion, "policyVersion");
            source = DomainPreconditions.requireText(source, "consentSource");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }
    }

    record Result(ResourceId consentRecordId, ConsentAction action) {
        public Result {
            DomainPreconditions.requireNonNull(consentRecordId, "consentRecordId");
            DomainPreconditions.requireNonNull(action, "consentAction");
        }
    }
}
