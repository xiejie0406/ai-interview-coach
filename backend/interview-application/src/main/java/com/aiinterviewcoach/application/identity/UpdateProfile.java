package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.application.shared.OperationContext;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.util.Optional;

@FunctionalInterface
public interface UpdateProfile {

    AccountView handle(Command command);

    record Command(
            Optional<String> displayName,
            Optional<Integer> javaExperienceYears,
            Optional<String> targetRole,
            Optional<String> targetLevel,
            AggregateVersion expectedVersion,
            OperationContext context
    ) {
        public Command {
            displayName = displayName == null ? Optional.empty() : displayName;
            javaExperienceYears = javaExperienceYears == null ? Optional.empty() : javaExperienceYears;
            targetRole = targetRole == null ? Optional.empty() : targetRole;
            targetLevel = targetLevel == null ? Optional.empty() : targetLevel;
            DomainPreconditions.require(displayName.isPresent() || javaExperienceYears.isPresent()
                            || targetRole.isPresent() || targetLevel.isPresent(),
                    DomainErrorCode.INVALID_ARGUMENT, "profile patch must contain at least one field");
            displayName = displayName.map(value -> DomainPreconditions.requireText(value, "displayName"));
            javaExperienceYears.ifPresent(value -> DomainPreconditions.require(value >= 0 && value <= 50,
                    DomainErrorCode.INVALID_ARGUMENT, "javaExperienceYears is out of range"));
            targetRole = targetRole.map(value -> DomainPreconditions.requireText(value, "targetRole"));
            targetLevel = targetLevel.map(value -> DomainPreconditions.requireText(value, "targetLevel"));
            DomainPreconditions.requireNonNull(expectedVersion, "expectedVersion");
            DomainPreconditions.requireNonNull(context, "operationContext");
            context.requirePrincipal();
        }

        @Override
        public String toString() {
            return "Command[displayName=" + (displayName.isPresent() ? "<present>" : "<absent>")
                    + ", javaExperienceYears=" + javaExperienceYears
                    + ", targetRole=" + targetRole + ", targetLevel=" + targetLevel
                    + ", expectedVersion=" + expectedVersion + ", context=" + context + "]";
        }
    }
}
