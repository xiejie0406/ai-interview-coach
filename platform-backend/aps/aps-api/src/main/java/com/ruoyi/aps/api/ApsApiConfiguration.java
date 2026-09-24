package com.ruoyi.aps.api;

import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * APS HTTP 适配层的总开关。
 *
 * <p>所有 APS HTTP 端点和 RuoYi 身份适配器都必须放在该功能开关边界内。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
public class ApsApiConfiguration
{
    @Bean(name = "apsAuditActorProvider")
    public ApsAuditActorProvider ruoyiApsAuditActorProvider()
    {
        return () -> new ApsAuditActor(String.valueOf(SecurityUtils.getUserId()), SecurityUtils.getUsername());
    }
}
