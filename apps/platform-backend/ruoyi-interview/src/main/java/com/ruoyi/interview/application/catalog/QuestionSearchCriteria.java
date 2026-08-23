package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.util.Optional;
import java.util.Set;

public record QuestionSearchCriteria(
        Optional<String> keyword,
        Set<String> topicCodes,
        Set<String> difficulties,
        Set<String> targetRoles,
        Optional<String> locale,
        Optional<String> cursor,
        int limit
) {

    public QuestionSearchCriteria {
        keyword = keyword == null ? Optional.empty() : keyword;
        topicCodes = Set.copyOf(topicCodes == null ? Set.of() : topicCodes);
        difficulties = Set.copyOf(difficulties == null ? Set.of() : difficulties);
        targetRoles = Set.copyOf(targetRoles == null ? Set.of() : targetRoles);
        locale = locale == null ? Optional.empty() : locale;
        cursor = cursor == null ? Optional.empty() : cursor;
        DomainPreconditions.require(limit > 0 && limit <= 100, DomainErrorCode.INVALID_ARGUMENT,
                "question search limit must be between 1 and 100");
    }
}
