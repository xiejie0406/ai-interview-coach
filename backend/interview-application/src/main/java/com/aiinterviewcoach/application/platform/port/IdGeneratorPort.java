package com.aiinterviewcoach.application.platform.port;

import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

public interface IdGeneratorPort {

    ResourceId nextResourceId();

    TenantId nextTenantId();

    UserId nextUserId();
}
