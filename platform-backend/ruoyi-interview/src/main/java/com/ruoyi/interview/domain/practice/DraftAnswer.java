package com.ruoyi.interview.domain.practice;

import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.time.Instant;

/** 尚未提交的私有草稿；持久化与日志适配必须按 Confidential 数据处理。 */
public record DraftAnswer(String text, String contentHash, Instant savedAt) {

    public DraftAnswer {
        text = DomainPreconditions.requireNonNull(text, "draftText");
        contentHash = DomainPreconditions.requireText(contentHash, "draftContentHash");
        DomainPreconditions.requireNonNull(savedAt, "draftSavedAt");
    }

    @Override
    public String toString() {
        return "DraftAnswer[text=<redacted>, contentHash=<redacted>, savedAt=" + savedAt + "]";
    }
}
