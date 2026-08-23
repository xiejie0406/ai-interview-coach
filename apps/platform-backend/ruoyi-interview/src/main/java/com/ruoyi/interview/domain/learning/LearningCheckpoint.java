package com.ruoyi.interview.domain.learning;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.time.Instant;

/** 学习进度检查点；summaryRef 指向正文。 */
@Deprecated(forRemoval = false)
public record LearningCheckpoint(
        ResourceId checkpointId,
        String summaryRef,
        Instant recordedAt
) {

    public LearningCheckpoint {
        DomainPreconditions.requireNonNull(checkpointId, "checkpointId");
        summaryRef = DomainPreconditions.requireText(summaryRef, "summaryRef");
        DomainPreconditions.requireNonNull(recordedAt, "recordedAt");
    }

    @Override
    public String toString() {
        return "LearningCheckpoint[checkpointId=" + checkpointId + ", summaryRef=<redacted>, recordedAt="
                + recordedAt + "]";
    }
}
