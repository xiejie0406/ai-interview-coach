import { apiRequest, apiRequestWithMeta, jsonBody, type ApiResponse } from '../../../shared/api/client';

export type OperationsView = 'providers' | 'jobs' | 'cost' | 'quality';
type ProjectionHeader = { projectionVersion: number; generatedAt: string; stale: boolean };
export type OperationsProjection =
  | (ProjectionHeader & { view: 'providers'; items: Array<{ capability: string; providerAlias: string; modelAlias: string; configVersion: string; state: 'HEALTHY' | 'DEGRADED' | 'UNAVAILABLE' | 'DISABLED'; lastSuccessAt: string | null; failureClass: string | null; latencyP95Ms: number | null }> })
  | (ProjectionHeader & { view: 'jobs'; items: Array<{ jobType: string; state: 'PENDING' | 'RUNNING' | 'RETRY_WAIT' | 'CANCEL_REQUESTED' | 'SUCCEEDED' | 'FAILED_FINAL' | 'CANCELLED'; count: number; oldestAgeSeconds: number }> })
  | (ProjectionHeader & { view: 'cost'; periodStart: string; periodEnd: string; items: Array<{ capability: string; currency: string; currencyExponent: number; amountMinor: number; invocationCount: number }> })
  | (ProjectionHeader & { view: 'quality'; items: Array<{ agentRole: string; schemaVersion: string; goldenSetVersion: string; status: 'PASS' | 'FAIL' | 'NOT_RUN' | 'BLOCKED'; sampleSize: number; passRateBasisPoints: number | null }> });

export type ImmutableVersionRef = { id: string; versionNo: number; contentHash: string };
export type AdminQuestionSummary = { id: string; stableKey: string; state: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'PUBLISHED_WITH_DRAFT' | 'PUBLISHED_WITH_REVIEW' | 'RETIRED'; currentDraftVersion: ImmutableVersionRef | null; currentPublishedVersion: ImmutableVersionRef | null; version: number };
export type QuestionVersionView = { id: string; versionNo: number; contentHash: string; title: string; stem: string; answerPoints: string[]; misconceptions: string[]; followUpTemplates: string[]; difficulty: 'JUNIOR' | 'MID' | 'SENIOR'; targetRoles: string[]; locale: string; contentSourceVersionId: string | null; createdAt: string };
export type RubricVersionView = { id: string; questionVersionId: string; versionNo: number; contentHash: string; dimensions: Array<{ code: string; description: string; evidenceRequired: boolean; criteria: string[] }>; refusalPolicy: string; createdAt: string };
export type AdminQuestionDetail = { question: AdminQuestionSummary; draft: QuestionVersionView | null; published: QuestionVersionView | null; rubric: RubricVersionView | null };
export type AdminQuestionPage = { items: AdminQuestionSummary[]; nextCursor: string | null };
export type AuditEventPage = { items: Array<{ id: string; actorType: 'USER' | 'SERVICE' | 'ADMIN'; actorId: string | null; actionCode: string; resourceType: string; resourceIdHash: string; outcome: 'ALLOWED' | 'DENIED' | 'FAILED'; reasonCode: string | null; correlationId: string; occurredAt: string }>; nextCursor: string | null };
export type QuestionDraftContent = { title: string; stem: string; answerPoints: string[]; misconceptions: string[]; followUpTemplates: string[]; difficulty: 'JUNIOR' | 'MID' | 'SENIOR'; targetRoles: Array<'JAVA_BACKEND' | 'AI_APPLICATION' | 'AGENT_ENGINEER'>; locale: 'zh-CN'; contentSourceVersionId: string | null };
export type FeatureFlagView = { id: string; state: 'DISABLED' | 'ACTIVE' | 'ROLLED_BACK'; safetyClass: 'NORMAL' | 'SAFETY_CRITICAL'; configurationVersion: string; activatedAt: string | null; expiresAt: string | null; version: number };

export const adminApi = {
  getOperationsProjection(view: OperationsView, signal?: AbortSignal) {
    return apiRequest<OperationsProjection>(`/admin/operations/${view}`, { signal });
  },

  listQuestions(signal?: AbortSignal) {
    return apiRequest<AdminQuestionPage>('/admin/questions', { signal });
  },

  createQuestion(stableKey: string, content: QuestionDraftContent, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<AdminQuestionDetail>('/admin/questions', { method: 'POST', body: jsonBody({ stableKey, content }), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },

  getQuestion(questionId: string, signal?: AbortSignal): Promise<ApiResponse<AdminQuestionDetail>> {
    return apiRequestWithMeta<AdminQuestionDetail>(`/admin/questions/${encodeURIComponent(questionId)}`, { signal });
  },

  createQuestionVersion(questionId: string, content: QuestionDraftContent, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<AdminQuestionDetail>(`/admin/questions/${encodeURIComponent(questionId)}/versions`, { method: 'POST', body: jsonBody(content), headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey }, signal });
  },

  createRubricVersion(questionId: string, request: { questionVersionId: string; dimensions: Array<{ code: string; description: string; evidenceRequired: boolean; criteria: string[] }>; refusalPolicy: string }, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<RubricVersionView>(`/admin/questions/${encodeURIComponent(questionId)}/rubrics`, { method: 'POST', body: jsonBody(request), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },

  applyQuestionCommand(questionId: string, command: 'submit-review' | 'reject-review' | 'publish' | 'retire', request: { questionVersionId?: string | null; rubricVersionId?: string | null; reasonCode: string }, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<AdminQuestionDetail>(`/admin/questions/${encodeURIComponent(questionId)}/commands/${command}`, { method: 'POST', body: jsonBody(request), headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey }, signal });
  },

  listAuditEvents(signal?: AbortSignal) {
    return apiRequest<AuditEventPage>('/admin/audit-events', { signal });
  },

  getFeatureFlag(flagId: string, signal?: AbortSignal): Promise<ApiResponse<FeatureFlagView>> {
    return apiRequestWithMeta<FeatureFlagView>(`/admin/feature-flags/${encodeURIComponent(flagId)}`, { signal });
  },

  applyFeatureFlagCommand(flagId: string, command: 'activate' | 'disable' | 'rollback', request: { reasonCode: string; expiresAt?: string | null }, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<FeatureFlagView>(`/admin/feature-flags/${encodeURIComponent(flagId)}/commands/${command}`, { method: 'POST', body: jsonBody(request), headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey }, signal });
  },
};
