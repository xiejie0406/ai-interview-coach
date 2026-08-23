package com.aiinterviewcoach.domain.evaluation;

import com.aiinterviewcoach.domain.platform.DomainErrorCode;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.HashSet;
import java.util.List;

/** report-composition-v1 section；正文可以持久化到受控加密列，但永不进入日志/toString。 */
public record ReportSection(
        String sectionId,
        String title,
        String body,
        List<String> judgementRefs,
        List<ResourceId> evidenceRefs
) {

    public ReportSection {
        sectionId = DomainPreconditions.requireText(sectionId, "sectionId");
        DomainPreconditions.require(sectionId.length() <= 128, DomainErrorCode.INVALID_ARGUMENT,
                "sectionId is too long");
        title = DomainPreconditions.requireText(title, "sectionTitle");
        DomainPreconditions.require(title.length() <= 200, DomainErrorCode.INVALID_ARGUMENT,
                "section title is too long");
        body = DomainPreconditions.requireText(body, "sectionBody");
        DomainPreconditions.require(body.length() <= 8000, DomainErrorCode.INVALID_ARGUMENT,
                "section body is too long");
        judgementRefs = List.copyOf(judgementRefs == null ? List.of() : judgementRefs);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        DomainPreconditions.require(judgementRefs.size() <= 64 && evidenceRefs.size() <= 64,
                DomainErrorCode.INVALID_ARGUMENT, "report reference count exceeds schema limit");
        DomainPreconditions.require(new HashSet<>(judgementRefs).size() == judgementRefs.size()
                        && new HashSet<>(evidenceRefs).size() == evidenceRefs.size(),
                DomainErrorCode.INVALID_ARGUMENT, "report references must be unique");
    }

    @Override
    public String toString() {
        return "ReportSection[sectionId=" + sectionId + ", title=<redacted>, body=<redacted>"
                + ", judgementRefCount=" + judgementRefs.size() + ", evidenceRefCount=" + evidenceRefs.size() + "]";
    }
}
