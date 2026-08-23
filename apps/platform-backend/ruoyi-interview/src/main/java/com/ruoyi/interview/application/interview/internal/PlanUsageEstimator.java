package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.domain.interview.PlannedQuestion;
import com.ruoyi.interview.domain.interview.UsageEstimate;

import java.util.List;

/** 由计量/定价实现注入；缺少该端口时不能猜测权益数量或金额。 */
@FunctionalInterface
public interface PlanUsageEstimator {

    UsageEstimate estimate(List<PlannedQuestion> questions, int followUpBudget);
}
