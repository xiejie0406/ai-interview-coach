package com.ruoyi.aden.application.error;

public final class AdenAccessDeniedException extends AdenApplicationException {
    public AdenAccessDeniedException() {
        super("ADEN_PERMISSION_DENIED", "当前账号无权执行此操作");
    }
}
