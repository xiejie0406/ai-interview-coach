package com.ruoyi.interview.application.interview.port;

import com.ruoyi.interview.domain.interview.InterviewPlan;
import com.ruoyi.interview.domain.interview.InterviewSession;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;

import java.util.Optional;

public interface InterviewRepository {

    Optional<InterviewPlan> findPlan(TenantId tenantId, ResourceId planId);

    Optional<InterviewSession> findSession(TenantId tenantId, ResourceId sessionId);

    Optional<InterviewSession> findSessionByPlan(TenantId tenantId, ResourceId planId, int planVersionNo);

    void savePlan(InterviewPlan plan);

    void saveSession(InterviewSession session);
}
