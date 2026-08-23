package com.aiinterviewcoach.application.interview;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.interview.InterviewMode;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.util.HashSet;
import java.util.Set;

/** 与 OpenAPI InterviewSetup 对齐；profile/budget/expiry 等派生事实由服务端 policy port 决定。 */
@FunctionalInterface
public interface CreateInterviewPlan {

    InterviewPlanView handle(Command command);

    record Command(
            InterviewTargetRole targetRole,
            InterviewTargetLevel targetLevel,
            Set<String> topics,
            int durationMinutes,
            InterviewMode mode,
            OperationContext context
    ) {
        public Command {
            DomainPreconditions.requireNonNull(targetRole, "targetRole");
            DomainPreconditions.requireNonNull(targetLevel, "targetLevel");
            topics = Set.copyOf(DomainPreconditions.requireNonEmpty(topics, "topics"));
            DomainPreconditions.require(topics.size() <= 9, DomainErrorCode.INVALID_ARGUMENT,
                    "topic count exceeds maximum");
            DomainPreconditions.require(new HashSet<>(topics).size() == topics.size(),
                    DomainErrorCode.INVALID_ARGUMENT, "topics must be unique");
            topics.forEach(value -> {
                DomainPreconditions.requireText(value, "topic");
                DomainPreconditions.require(value.length() <= 96, DomainErrorCode.INVALID_ARGUMENT,
                        "topic is too long");
            });
            DomainPreconditions.require(durationMinutes >= 5 && durationMinutes <= 60,
                    DomainErrorCode.INVALID_ARGUMENT, "durationMinutes is out of range");
            DomainPreconditions.requireNonNull(mode, "interviewMode");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[targetRole=" + targetRole + ", targetLevel=" + targetLevel
                    + ", topics=<redacted>, durationMinutes=" + durationMinutes + ", mode=" + mode
                    + ", context=" + context + "]";
        }
    }
}
