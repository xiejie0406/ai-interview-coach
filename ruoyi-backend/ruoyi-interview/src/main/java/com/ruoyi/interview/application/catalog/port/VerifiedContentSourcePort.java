package com.ruoyi.interview.application.catalog.port;

import com.ruoyi.interview.domain.catalog.ContentSourceReference;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

/**
 * 只从服务端 Source/Publication 事实解析已验证且许可可用的来源；请求体不得提交 verified 结论。
 */
public interface VerifiedContentSourcePort {

    Optional<ContentSourceReference> findVerified(
            TenantId tenantId,
            ResourceId contentSourceVersionId
    );
}
