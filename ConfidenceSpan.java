package com.aiinterviewcoach.domain.voice;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

import java.math.BigDecimal;

/** ASR 低置信区间；offset unit 由所属 TranscriptVersion 显式声明。 */
public record ConfidenceSpan(int startInclusive, int endExclusive, BigDecimal confidence) {

    public ConfidenceSpan {
        DomainPreconditions.require(startInclusive >= 0 && endExclusive > startInclusive,
                DomainErrorCode.INVALID_ARGUMENT, "confidence span range is invalid");
        DomainPreconditions.requireNonNull(confidence, "confidence");
        DomainPreconditions.require(confidence.compareTo(BigDecimal.ZERO) >= 0
                        && confidence.compareTo(BigDecimal.ONE) <= 0,
                DomainErrorCode.INVALID_ARGUMENT, "confidence must be between zero and one");
    }
}
