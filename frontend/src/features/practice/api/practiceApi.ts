import { apiRequest, apiRequestWithMeta, jsonBody, type ApiResponse } from '../../../shared/api/client';

export type EvaluationStatus = 'NOT_REQUESTED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'UNAVAILABLE';

export type PracticeAttemptView = {
  id: string;
  questionVersionId: string;
  state: 'DRAFT' | 'SUBMITTED' | 'CANCELLED';
  answerVersions: Array<{ id: string; version: number; submittedAt: string }>;
  evaluationStatus?: EvaluationStatus;
  version: number;
};

export const practiceApi = {
  start(questionVersionId: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<PracticeAttemptView>('/practice-attempts', {
      method: 'POST',
      body: jsonBody({ questionVersionId }),
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  listHistory(signal?: AbortSignal) {
    return apiRequest<PracticeAttemptView[]>('/practice-attempts', { signal });
  },

  saveDraft(attemptId: string, text: string, etag: string, idempotencyKey: string, signal?: AbortSignal): Promise<ApiResponse<PracticeAttemptView>> {
    return apiRequestWithMeta<PracticeAttemptView>(`/practice-attempts/${encodeURIComponent(attemptId)}/draft`, {
      method: 'PUT',
      body: jsonBody({ text }),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  submit(attemptId: string, text: string, etag: string, idempotencyKey: string, contentHash?: string, signal?: AbortSignal) {
    return apiRequestWithMeta<PracticeAttemptView>(`/practice-attempts/${encodeURIComponent(attemptId)}/submit`, {
      method: 'POST',
      body: jsonBody({ text, ...(contentHash ? { contentHash } : {}) }),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },
};
