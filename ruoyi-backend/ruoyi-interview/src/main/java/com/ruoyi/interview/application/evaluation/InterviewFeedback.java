package com.ruoyi.interview.application.evaluation;

import java.util.List;

/** 首版会话反馈：保留证据化分类，不生成综合数值分数。 */
public record InterviewFeedback(String interviewId, String status, String model, String createdAt,
        List<TurnFeedback> turns, List<String> limitations) {
    public record TurnFeedback(String turnId, String question, String answerVersionId, String answer,
            String rubricVersionId, List<DimensionFeedback> dimensions) { }
    public record DimensionFeedback(String code, String criterion, String judgement, String quote, String feedback) { }
}
