package com.ruoyi.interview.application.voice;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface CheckVoicePreflight {

    Result handle(Query query);

    record Query(ResourceId interviewId, ResourceId turnId,
                 List<String> codecCandidates, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(interviewId, "interviewId");
            DomainPreconditions.requireNonNull(turnId, "turnId");
            codecCandidates = List.copyOf(DomainPreconditions.requireNonEmpty(
                    codecCandidates, "codecCandidates"));
            codecCandidates.forEach(codec -> DomainPreconditions.requireText(codec, "codecCandidate"));
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }

    record Result(boolean enabled, boolean consentRequired, List<String> supportedCodecs,
                  int maximumDurationSeconds, long maximumBytes,
                  Optional<String> unavailableReasonCode) {
        public Result {
            supportedCodecs = List.copyOf(supportedCodecs == null ? List.of() : supportedCodecs);
            supportedCodecs.forEach(codec -> DomainPreconditions.requireText(codec, "supportedCodec"));
            DomainPreconditions.require(maximumDurationSeconds > 0 && maximumBytes > 0,
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "voice limits must be positive");
            unavailableReasonCode = unavailableReasonCode == null
                    ? Optional.empty() : unavailableReasonCode;
            DomainPreconditions.require(!enabled || (!consentRequired && !supportedCodecs.isEmpty()),
                    com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                    "enabled voice preflight is inconsistent");
        }
    }
}
