package com.ruoyi.aps.api.dto;

import java.util.List;
import com.ruoyi.aps.application.foundation.ApsValidationIssue;

/**
 * 与 problem-v1.schema.json 必填字段一致的最小错误信封。
 */
public record ApsProblem(
        String schemaVersion,
        String contractType,
        String problemId,
        String reasonCode,
        String severity,
        String title,
        String detail,
        boolean retryable,
        List<Object> objectRefs)
{
    public static ApsProblem error(String problemId, String reasonCode, String title, String detail,
            boolean retryable)
    {
        return new ApsProblem("1.0", "APS_PROBLEM", problemId, reasonCode, "ERROR", title, detail,
                retryable, List.of());
    }

    public static ApsProblem validation(String problemId, String reasonCode, String title, String detail,
            List<ApsValidationIssue> issues)
    {
        List<Object> references = issues.stream().limit(32)
                .map(issue -> (Object) new ObjectRef(issue.objectType(), issue.objectId(), issue.field())).toList();
        return new ApsProblem("1.0", "APS_PROBLEM", problemId, reasonCode, "ERROR", title, detail,
                false, references);
    }

    public record ObjectRef(String objectType, String objectId, String field) { }
}
