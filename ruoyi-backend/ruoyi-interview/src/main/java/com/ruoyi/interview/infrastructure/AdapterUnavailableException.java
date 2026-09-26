package com.ruoyi.interview.infrastructure;

/** 出站能力未配置时使用的稳定业务异常；不暴露供应商凭据或响应正文。 */
public final class AdapterUnavailableException extends IllegalStateException {
    private final String capability;

    public AdapterUnavailableException(String capability) {
        super("outbound adapter is not configured: " + capability);
        this.capability = capability;
    }

    public String capability() {
        return capability;
    }
}
