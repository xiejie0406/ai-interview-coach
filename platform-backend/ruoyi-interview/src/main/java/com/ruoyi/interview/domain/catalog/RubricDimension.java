package com.ruoyi.interview.domain.catalog;

import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.util.List;

/** Rubric 的一个可解释维度；评分结果不属于题库版本。 */
public record RubricDimension(
        String code,
        String description,
        boolean evidenceRequired,
        List<String> criteria
) {

    public RubricDimension {
        code = DomainPreconditions.requireText(code, "rubricDimensionCode");
        description = DomainPreconditions.requireText(description, "rubricDimensionDescription");
        criteria = List.copyOf(DomainPreconditions.requireNonEmpty(criteria, "rubricCriteria"));
        criteria.forEach(item -> DomainPreconditions.requireText(item, "rubricCriterion"));
    }

    @Override
    public String toString() {
        return "RubricDimension[code=" + code + ", description=<redacted>, evidenceRequired="
                + evidenceRequired + ", criteria=<redacted:" + criteria.size() + ">]";
    }
}
