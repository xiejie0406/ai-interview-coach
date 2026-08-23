package com.aiinterviewcoach.adapters.outbound;

/** 不携带供应商、凭据或 payload 细节的稳定基础设施失败。 */
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
