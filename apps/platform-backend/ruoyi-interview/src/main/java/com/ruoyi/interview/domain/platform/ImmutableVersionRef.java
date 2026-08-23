package com.ruoyi.interview.domain.platform;

/** 指向不可变内容、配置或报告版本的引用。 */
public record ImmutableVersionRef(ResourceId resourceId, int versionNo, String contentHash) {

    public ImmutableVersionRef {
        DomainPreconditions.requireNonNull(resourceId, "resourceId");
        DomainPreconditions.require(versionNo > 0, DomainErrorCode.INVALID_ARGUMENT,
                "version number must be positive");
        contentHash = DomainPreconditions.requireText(contentHash, "contentHash");
    }
}
