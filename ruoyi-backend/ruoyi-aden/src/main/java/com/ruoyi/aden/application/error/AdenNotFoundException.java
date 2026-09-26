package com.ruoyi.aden.application.error;

public final class AdenNotFoundException extends AdenApplicationException {
    public AdenNotFoundException() {
        super("ADEN_TASK_NOT_FOUND", "请求的资源不存在");
    }
}
