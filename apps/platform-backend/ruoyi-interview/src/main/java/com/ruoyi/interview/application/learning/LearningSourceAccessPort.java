package com.ruoyi.interview.application.learning;

import com.ruoyi.interview.application.agent.learning.LearningCoachPort;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;

import java.util.List;

/** owner-scoped ReportVersion 与 allowlisted QuestionVersion snapshot owner port。 */
public interface LearningSourceAccessPort {

    Source load(TenantId tenantId, UserId userId, ResourceId reportId);

    record Source(boolean allowed, LearningCoachPort.ReportSnapshot report,
                  List<LearningCoachPort.AllowedQuestionVersion> allowedQuestions) {
        public Source {
            allowedQuestions = List.copyOf(allowedQuestions == null ? List.of() : allowedQuestions);
            if (allowed && report == null) {
                throw new IllegalArgumentException("allowed learning source requires a report snapshot");
            }
            if (!allowed && (report != null || !allowedQuestions.isEmpty())) {
                throw new IllegalArgumentException("denied learning source must not reveal metadata");
            }
        }
    }
}
