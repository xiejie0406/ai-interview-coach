package com.aiinterviewcoach.application.interview.port;

import com.aiinterviewcoach.application.interview.InterviewTargetLevel;
import com.aiinterviewcoach.application.interview.InterviewTargetRole;
import com.aiinterviewcoach.domain.interview.InterviewMode;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.TimeBudget;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.Set;

/** 把客户端 Setup 解析为服务端拥有的 profile/budget/expiry/selection policy。 */
@FunctionalInterface
public interface InterviewPlanPolicyPort {

    Resolved resolve(Request request);

    record Request(
            TenantId tenantId,
            UserId userId,
            InterviewTargetRole targetRole,
            InterviewTargetLevel targetLevel,
            Set<String> topics,
            int durationMinutes,
            InterviewMode mode,
            Instant requestedAt
    ) {
        public Request {
            DomainPreconditions.requireNonNull(tenantId, "tenantId");
            DomainPreconditions.requireNonNull(userId, "userId");
            DomainPreconditions.requireNonNull(targetRole, "targetRole");
            DomainPreconditions.requireNonNull(targetLevel, "targetLevel");
            topics = Set.copyOf(DomainPreconditions.requireNonEmpty(topics, "topics"));
            DomainPreconditions.require(durationMinutes >= 5 && durationMinutes <= 60,
                    DomainErrorCode.INVALID_ARGUMENT, "durationMinutes is out of range");
            DomainPreconditions.requireNonNull(mode, "interviewMode");
            DomainPreconditions.requireNonNull(requestedAt, "requestedAt");
        }
    }

    record Resolved(
            String policyVersion,
            ImmutableVersionRef profileVersion,
            Set<String> topicCodes,
            Set<String> targetRoles,
            Set<String> difficulties,
            TimeBudget timeBudget,
            int questionCount,
            int followUpBudget,
            Instant expiresAt
    ) {
        public Resolved {
            policyVersion = DomainPreconditions.requireText(policyVersion, "interviewPlanPolicyVersion");
            DomainPreconditions.require(policyVersion.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                    "interviewPlanPolicyVersion is too long");
            DomainPreconditions.requireNonNull(profileVersion, "profileVersion");
            topicCodes = Set.copyOf(DomainPreconditions.requireNonEmpty(topicCodes, "topicCodes"));
            targetRoles = Set.copyOf(DomainPreconditions.requireNonEmpty(targetRoles, "targetRoles"));
            difficulties = Set.copyOf(DomainPreconditions.requireNonEmpty(difficulties, "difficulties"));
            DomainPreconditions.requireNonNull(timeBudget, "timeBudget");
            DomainPreconditions.require(questionCount > 0, DomainErrorCode.INVALID_ARGUMENT,
                    "questionCount must be positive");
            DomainPreconditions.require(followUpBudget >= 0, DomainErrorCode.INVALID_ARGUMENT,
                    "followUpBudget must not be negative");
            DomainPreconditions.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
