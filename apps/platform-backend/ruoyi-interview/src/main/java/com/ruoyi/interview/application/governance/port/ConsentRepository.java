package com.ruoyi.interview.application.governance.port;

import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.governance.ConsentRecord;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository {

    void append(ConsentRecord record);

    List<ConsentRecord> history(TenantId tenantId, UserId userId, ConsentPurpose purpose);

    Optional<ConsentRecord> current(TenantId tenantId, UserId userId, ConsentPurpose purpose);
}
