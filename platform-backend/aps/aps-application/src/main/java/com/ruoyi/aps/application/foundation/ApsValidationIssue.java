package com.ruoyi.aps.application.foundation;

/** 可安全暴露给调用方的对象级业务校验问题。 */
public record ApsValidationIssue(String code, String objectType, String objectId, String field, String message)
{
}
