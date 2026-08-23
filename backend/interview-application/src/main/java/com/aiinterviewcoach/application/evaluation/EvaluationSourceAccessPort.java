package com.aiinterviewcoach.application.evaluation;

import com.aiinterviewcoach.domain.evaluation.EvaluationSourceRef;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

/** Answer owner 返回固定 source metadata 与受控正文引用；不暴露 Practice/Interview Repository。 */
public interface EvaluationSourceAccessPort {

    Source inspect(TenantId tenantId, UserId userId, ResourceId answerVersionId);

    String loadContentReference(TenantId tenantId, UserId userId, EvaluationSourceRef sourceRef);

    record Source(boolean allowed, EvaluationSourceRef sourceRef) {
        public Source {
            if (allowed) {
                DomainPreconditions.requireNonNull(sourceRef, "evaluationSourceRef");
            } else if (sourceRef != null) {
                throw new IllegalArgumentException("denied evaluation source must not reveal metadata");
            }
        }

        @Override
        public String toString() {
            return "Source[allowed=" + allowed + ", sourceRef=" + sourceRef + "]";
        }
    }
}
