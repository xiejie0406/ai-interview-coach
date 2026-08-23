package com.aiinterviewcoach.domain.learning;

import com.aiinterviewcoach.domain.platform.PromptSchemaPin;
import com.aiinterviewcoach.domain.platform.ProviderPolicySnapshot;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** Learning Coach 的结构化建议元数据；contentRef 指向正文。 */
@Deprecated(forRemoval = false)
public record LearningRecommendation(
        PromptSchemaPin coachSchemaPin,
        ProviderPolicySnapshot providerPolicySnapshot,
        String contentRef
) {

    public LearningRecommendation {
        DomainPreconditions.requireNonNull(coachSchemaPin, "coachSchemaPin");
        DomainPreconditions.requireNonNull(providerPolicySnapshot, "providerPolicySnapshot");
        contentRef = DomainPreconditions.requireText(contentRef, "contentRef");
    }

    @Override
    public String toString() {
        return "LearningRecommendation[coachSchemaPin=" + coachSchemaPin
                + ", providerPolicySnapshot=" + providerPolicySnapshot.routePlanId()
                + ", contentRef=<redacted>]";
    }
}
