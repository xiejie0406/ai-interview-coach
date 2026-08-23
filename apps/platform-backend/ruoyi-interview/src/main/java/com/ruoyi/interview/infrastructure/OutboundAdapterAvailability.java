package com.ruoyi.interview.infrastructure;

/** 出站适配器的非敏感运行元数据，不参与认证、授权或主体解析。 */
public interface OutboundAdapterAvailability {
    String adapterId();
    boolean available();
    String reasonCode();
}
