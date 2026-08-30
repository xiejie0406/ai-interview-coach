package com.ruoyi.interview.domain.evaluation;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.List;

/** 经 report-composition-v1 Schema 验证、尚未发布的结构化候选。 */
public record ReportComposition(
        ResourceId evaluationVersionId,
        List<ReportSection> sections,
        List<String> actions,
        List<String> limitations
) {

    public ReportComposition {
        DomainPreconditions.requireNonNull(evaluationVersionId, "evaluationVersionId");
        sections = List.copyOf(sections == null ? List.of() : sections);
        DomainPreconditions.require(sections.size() <= 24, DomainErrorCode.INVALID_ARGUMENT,
                "report section count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(sections.stream().map(ReportSection::sectionId).toList()).size()
                        == sections.size(), DomainErrorCode.INVALID_ARGUMENT,
                "report section IDs must be unique");
        actions = boundedText(actions, 3, 500, "report action");
        limitations = boundedText(limitations, 16, 500, "report limitation");
    }

    private static List<String> boundedText(List<String> values, int maxItems, int maxLength, String name) {
        values = List.copyOf(values == null ? List.of() : values);
        DomainPreconditions.require(values.size() <= maxItems, DomainErrorCode.INVALID_ARGUMENT,
                name + " count exceeds schema limit");
        values.forEach(value -> {
            DomainPreconditions.requireText(value, name);
            DomainPreconditions.require(value.length() <= maxLength, DomainErrorCode.INVALID_ARGUMENT,
                    name + " is too long");
        });
        return values;
    }

    @Override
    public String toString() {
        return "ReportComposition[evaluationVersionId=" + evaluationVersionId
                + ", sectionCount=" + sections.size() + ", actionCount=" + actions.size()
                + ", limitationCount=" + limitations.size() + "]";
    }
}
