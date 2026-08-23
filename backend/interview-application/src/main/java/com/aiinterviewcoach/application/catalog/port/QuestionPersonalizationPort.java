package com.aiinterviewcoach.application.catalog.port;

import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.domain.platform.UserId;

import java.time.Instant;
import java.util.Optional;

/** 公共题目新增与用户级答案覆盖端口。身份只能来自服务端 Session。 */
public interface QuestionPersonalizationPort {
    ResourceId createPublicQuestion(CreatePublicQuestion command);

    Optional<String> findUserAnswer(TenantId catalogTenantId, TenantId ownerTenantId,
                                    UserId userId, ResourceId questionId);

    void saveUserAnswer(TenantId catalogTenantId, TenantId ownerTenantId,
                        UserId userId, ResourceId questionId, String answer, Instant now);

    record CreatePublicQuestion(
            TenantId catalogTenantId,
            TenantId creatorTenantId,
            UserId createdBy,
            String category,
            String difficulty,
            String title,
            String prompt,
            String systemAnswer,
            Instant now
    ) { }
}
