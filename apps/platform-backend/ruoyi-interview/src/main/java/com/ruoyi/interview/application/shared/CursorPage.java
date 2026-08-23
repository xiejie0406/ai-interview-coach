package com.ruoyi.interview.application.shared;

import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.util.List;
import java.util.Optional;

/** 不透明 cursor 分页结果；排序稳定性由 owner query port 保证。 */
public record CursorPage<T>(List<T> items, Optional<String> nextCursor) {

    public CursorPage {
        items = List.copyOf(DomainPreconditions.requireNonNull(items, "items"));
        nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
        nextCursor.ifPresent(cursor -> DomainPreconditions.requireText(cursor, "nextCursor"));
    }
}
