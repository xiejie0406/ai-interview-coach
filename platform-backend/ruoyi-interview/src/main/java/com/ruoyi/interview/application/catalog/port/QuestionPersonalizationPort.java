package com.ruoyi.interview.application.catalog.port;

import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** 用户级答案覆盖端口。V2 公共题目只能通过 Admin Catalog 工作流创建。 */
public interface QuestionPersonalizationPort {
    Optional<String> findUserAnswer(TenantId catalogTenantId, TenantId ownerTenantId,
                                    UserId userId, ResourceId questionId);

    void saveUserAnswer(TenantId catalogTenantId, TenantId ownerTenantId,
                        UserId userId, ResourceId questionId, String answer, Instant now);
}
