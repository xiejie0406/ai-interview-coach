package com.aiinterviewcoach.adapters.outbound;

/** 出站适配器占位的非敏感运行元数据；它不是 application port，也不能用于授权。 */
public interface OutboundAdapterAvailability {
    String adapterId();

    boolean available();

    String reasonCode();
}
