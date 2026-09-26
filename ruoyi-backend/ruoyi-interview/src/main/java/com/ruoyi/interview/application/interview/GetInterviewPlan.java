package com.ruoyi.interview.application.interview;

import com.ruoyi.interview.application.shared.QueryContext;
import com.ruoyi.interview.domain.platform.DomainPreconditions;
import com.ruoyi.interview.domain.platform.ResourceId;

/**
 * 读取当前用户自己的面试计划快照。
 *
 * <p>计划正文中的题目内容不会从该查询泄露；调用方只得到与 OpenAPI
 * {@code InterviewPlanView} 对齐的聚合投影。</p>
 */
@FunctionalInterface
public interface GetInterviewPlan {

    InterviewPlanView handle(Query query);

    record Query(ResourceId planId, QueryContext context) {
        public Query {
            DomainPreconditions.requireNonNull(planId, "planId");
            DomainPreconditions.requireNonNull(context, "queryContext");
        }
    }
}
