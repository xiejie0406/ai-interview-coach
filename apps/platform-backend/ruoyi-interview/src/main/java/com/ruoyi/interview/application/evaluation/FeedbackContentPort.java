package com.ruoyi.interview.application.evaluation;

import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

/** 私密反馈正文 owner；返回受控引用，日志和事件不得包含原文。 */
public interface FeedbackContentPort {
    String store(TenantId tenantId, UserId userId, ResourceId evaluationId,
                 ResourceId feedbackId, String comment);
}
