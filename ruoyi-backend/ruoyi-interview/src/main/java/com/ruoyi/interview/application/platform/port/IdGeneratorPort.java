package com.ruoyi.interview.application.platform.port;

import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

public interface IdGeneratorPort {

    ResourceId nextResourceId();

    TenantId nextTenantId();

    UserId nextUserId();
}
