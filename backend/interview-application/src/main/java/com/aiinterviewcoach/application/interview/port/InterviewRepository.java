package com.aiinterviewcoach.application.interview.port;

import com.aiinterviewcoach.domain.interview.InterviewPlan;
import com.aiinterviewcoach.domain.interview.InterviewSession;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.util.Optional;

public interface InterviewRepository {

    Optional<InterviewPlan> findPlan(TenantId tenantId, ResourceId planId);

    Optional<InterviewSession> findSession(TenantId tenantId, ResourceId sessionId);

    Optional<InterviewSession> findSessionByPlan(TenantId tenantId, ResourceId planId, int planVersionNo);

    void savePlan(InterviewPlan plan);

    void saveSession(InterviewSession session);
}
