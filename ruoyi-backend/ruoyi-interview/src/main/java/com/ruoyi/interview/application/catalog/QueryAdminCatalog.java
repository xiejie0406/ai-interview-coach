package com.ruoyi.interview.application.catalog;

import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.application.shared.OperationContext;
import com.ruoyi.interview.domain.catalog.QuestionStatus;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

/** 受保护的 Admin 题库查询；tenant 由服务端配置传入，主体只来自 RuoYi SecurityContext。 */
public interface QueryAdminCatalog {

    CursorPage<AdminQuestionSummary> search(
            TenantId catalogTenantId,
            Optional<String> category,
            Optional<QuestionStatus> state,
            Optional<String> cursor,
            int limit,
            OperationContext context
    );

    AdminQuestionDetail get(
            TenantId catalogTenantId,
            ResourceId questionId,
            OperationContext context
    );

    /**
     * 按不可变 Rubric 版本 ID 读取治理事实；不能用题目 latest 投影代替，避免并发写入/幂等重放返回错版本。
     */
    Optional<RubricVersion> getRubricVersion(
            TenantId catalogTenantId,
            ResourceId rubricVersionId,
            OperationContext context
    );
}
