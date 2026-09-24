package com.ruoyi.aps.application.foundation;

import java.util.Objects;

/**
 * 可安全映射到公共 Problem 信封的业务异常。
 */
public class ApsBusinessException extends RuntimeException
{
    private final ApsErrorCode errorCode;

    public ApsBusinessException(ApsErrorCode errorCode, String message)
    {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ApsErrorCode errorCode()
    {
        return errorCode;
    }
}
