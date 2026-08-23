package com.aiinterviewcoach.adapters.inbound.rest.health;

/** Boot 向健康适配器提供公开、非敏感的构建元数据；适配器不直接读取环境变量。 */
public interface ServiceMetadata {
    String serviceName();

    String releaseVersion();
}
