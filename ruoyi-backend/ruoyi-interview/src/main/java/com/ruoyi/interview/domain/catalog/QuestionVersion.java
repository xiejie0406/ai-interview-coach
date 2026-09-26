package com.ruoyi.interview.domain.catalog;

import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** 发布后不可变的题目内容版本。 */
public record QuestionVersion(
        ResourceId id,
        TenantId tenantId,
        ResourceId questionId,
        int versionNo,
        String contentHash,
        String category,
        String title,
        String stem,
        List<String> answerPoints,
        List<String> misconceptions,
        List<String> followUpTemplates,
        String difficulty,
        List<String> targetRoles,
        String locale,
        Optional<ContentSourceReference> source,
        UserId authoredBy,
        Optional<UserId> reviewedBy,
        Instant createdAt
) {

    public QuestionVersion {
        DomainPreconditions.requireNonNull(id, "questionVersionId");
        DomainPreconditions.requireNonNull(tenantId, "tenantId");
        DomainPreconditions.requireNonNull(questionId, "questionId");
        DomainPreconditions.require(versionNo > 0, com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                "question version number must be positive");
        contentHash = DomainPreconditions.requireText(contentHash, "questionContentHash");
        category = QuestionCategory.requireStored(category);
        title = DomainPreconditions.requireText(title, "questionTitle");
        stem = DomainPreconditions.requireText(stem, "questionStem");
        answerPoints = immutableTextList(answerPoints, "answerPoints", false);
        misconceptions = immutableTextList(misconceptions, "misconceptions", false);
        followUpTemplates = immutableTextList(followUpTemplates, "followUpTemplates", false);
        difficulty = DomainPreconditions.requireText(difficulty, "difficulty");
        targetRoles = immutableTextList(targetRoles, "targetRoles", true);
        locale = DomainPreconditions.requireText(locale, "locale");
        source = source == null ? Optional.empty() : source;
        DomainPreconditions.requireNonNull(authoredBy, "authoredBy");
        reviewedBy = reviewedBy == null ? Optional.empty() : reviewedBy;
        DomainPreconditions.requireNonNull(createdAt, "createdAt");
    }

    public ImmutableVersionRef versionRef() {
        return new ImmutableVersionRef(id, versionNo, contentHash);
    }

    @Override
    public String toString() {
        return "QuestionVersion[id=" + id + ", tenantId=" + tenantId + ", questionId=" + questionId
                + ", versionNo=" + versionNo + ", contentHash=" + contentHash
                + ", category=" + category
                + ", title=<redacted>, stem=<redacted>, answerPoints=<redacted:" + answerPoints.size()
                + ">, misconceptions=<redacted:" + misconceptions.size() + ">, followUpTemplates=<redacted:"
                + followUpTemplates.size() + ">, difficulty=" + difficulty + ", targetRoles=" + targetRoles
                + ", locale=" + locale + ", source=" + source.map(ContentSourceReference::sourceId)
                + ", authoredBy=" + authoredBy + ", reviewedBy=" + reviewedBy + ", createdAt=" + createdAt + "]";
    }

    private static List<String> immutableTextList(List<String> values, String name, boolean required) {
        DomainPreconditions.requireNonNull(values, name);
        if (required) {
            DomainPreconditions.requireNonEmpty(values, name);
        }
        List<String> result = List.copyOf(values);
        result.forEach(value -> DomainPreconditions.requireText(value, name + " item"));
        DomainPreconditions.require(new HashSet<>(result).size() == result.size(),
                com.ruoyi.interview.domain.platform.DomainErrorCode.INVALID_ARGUMENT,
                name + " must not contain duplicates");
        return result;
    }
}
