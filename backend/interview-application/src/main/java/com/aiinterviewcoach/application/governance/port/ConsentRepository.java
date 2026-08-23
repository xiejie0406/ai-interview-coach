package com.aiinterviewcoach.application.governance.port;

import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.governance.ConsentRecord;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository {

    void append(ConsentRecord record);

    List<ConsentRecord> history(TenantId tenantId, UserId userId, ConsentPurpose purpose);

    Optional<ConsentRecord> current(TenantId tenantId, UserId userId, ConsentPurpose purpose);
}
