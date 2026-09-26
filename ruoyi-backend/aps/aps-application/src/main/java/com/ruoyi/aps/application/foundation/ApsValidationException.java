package com.ruoyi.aps.application.foundation;

import java.util.List;

/** 携带对象定位信息的业务校验失败。 */
public final class ApsValidationException extends ApsBusinessException
{
    private final List<ApsValidationIssue> issues;

    public ApsValidationException(ApsErrorCode errorCode, String message, List<ApsValidationIssue> issues)
    {
        super(errorCode, message);
        this.issues = List.copyOf(issues);
    }

    public List<ApsValidationIssue> issues()
    {
        return issues;
    }
}
