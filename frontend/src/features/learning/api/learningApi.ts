import { apiRequest, apiRequestWithMeta, jsonBody, type ApiResponse } from '../../../shared/api/client';

export type LearningPlanView = {
  id: string;
  state: 'CANDIDATE' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
  sourceReportVersionId: string;
  items: Array<{
    id: string;
    questionVersionId: string;
    questionTitle: string;
    state: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED';
    reasonCodes: string[];
    scheduledAt: string | null;
    version: number;
  }>;
  limitations: string[];
  version: number;
};

export type LearningPlanPage = { items: LearningPlanView[]; nextCursor: string | null };
export type DashboardTrend = { dimensionId: string; comparable: boolean; reasonCode: string; direction: 'IMPROVING' | 'STABLE' | 'DECLINING' | 'UNKNOWN'; sampleCount: number };
export type DashboardView = {
  projectionVersion: number;
  projectionGeneratedAt: string;
  refreshState: 'FRESH' | 'REFRESHING' | 'STALE' | 'FAILED';
  todayItems: Array<{ learningItemId: string; learningPlanId: string; questionVersionId: string; questionTitle: string; state: 'PENDING' | 'IN_PROGRESS'; scheduledAt: string | null; reasonCodes: string[]; version: number }>;
  recentReports: Array<{ reportId: string; reportVersionId: string | null; state: 'PENDING' | 'RUNNING' | 'READY' | 'PARTIAL' | 'FAILED' | 'CANCELLED'; completedAt: string | null; limitationCount: number }>;
  trends: DashboardTrend[];
};

export const learningApi = {
  getDashboard(signal?: AbortSignal) {
    return apiRequest<DashboardView>('/dashboard', { signal });
  },

  listPlans(cursor?: string, signal?: AbortSignal) {
    const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
    return apiRequest<LearningPlanPage>(`/learning-plans${query}`, { signal });
  },

  getPlan(planId: string, signal?: AbortSignal): Promise<ApiResponse<LearningPlanView>> {
    return apiRequestWithMeta<LearningPlanView>(`/learning-plans/${encodeURIComponent(planId)}`, { signal });
  },

  createPlanCandidate(reportId: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<LearningPlanView>(`/reports/${encodeURIComponent(reportId)}/learning-plans`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  applyPlanCommand(planId: string, command: 'confirm' | 'cancel', etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<LearningPlanView>(`/learning-plans/${encodeURIComponent(planId)}/commands/${command}`, {
      method: 'POST',
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  applyItemCommand(itemId: string, command: 'complete' | 'skip' | 'reschedule', request: { scheduledAt?: string; reasonCode?: string }, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<LearningPlanView>(`/learning-items/${encodeURIComponent(itemId)}/commands/${command}`, {
      method: 'POST',
      body: jsonBody(request),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },
};
