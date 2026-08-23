import { apiRequest, jsonBody } from '../../../shared/api/client';

export type EvaluationView = {
  id: string;
  state: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED_RETRYABLE' | 'FAILED_FINAL' | 'CANCELLED';
  stage: 'QUEUED' | 'EVIDENCE_EXTRACTING' | 'RUBRIC_JUDGING' | 'REPORT_COMPOSING' | 'MANUAL_REVIEW' | 'COMPLETE';
  inputVersionId: string;
  configVersionId: string;
  evaluationVersionId: string | null;
  failureCode: string | null;
  dimensions: Array<{
    dimensionId: string;
    judgement: 'CORRECT' | 'PARTIAL' | 'INCORRECT' | 'INSUFFICIENT' | 'CONFLICTING';
    confidence: 'LOW' | 'MEDIUM' | 'HIGH';
    insufficientEvidence: boolean;
    reasonCodes: string[];
    evidenceRefs: string[];
  }>;
  limitations: string[];
  streamCursor: string | null;
  version: number;
};

export type ReportView = {
  id: string;
  evaluationId: string;
  sourceInterviewId: string;
  state: 'PENDING' | 'RUNNING' | 'READY' | 'PARTIAL' | 'FAILED' | 'CANCELLED';
  reportVersionId?: string | null;
  evaluationVersionId?: string | null;
  sections?: Array<{ sectionId: string; title: string; body: string; judgementRefs: string[]; evidenceRefs: string[] }>;
  actions?: string[];
  limitations?: string[];
  version: number;
};

export type OperationAccepted = { operationId: string; statusUrl: string };

export const evaluationApi = {
  requestAnswerEvaluation(answerVersionId: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<OperationAccepted>(`/answers/${encodeURIComponent(answerVersionId)}/evaluations`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  getEvaluation(evaluationId: string, signal?: AbortSignal) {
    return apiRequest<EvaluationView>(`/evaluations/${encodeURIComponent(evaluationId)}`, { signal });
  },

  getReport(reportId: string, signal?: AbortSignal) {
    return apiRequest<ReportView>(`/reports/${encodeURIComponent(reportId)}`, { signal });
  },

  getInterviewReport(interviewId: string, signal?: AbortSignal) {
    return apiRequest<ReportView>(`/interviews/${encodeURIComponent(interviewId)}/report`, { signal });
  },

  submitFeedback(evaluationId: string, request: { type: 'INACCURATE' | 'UNHELPFUL' | 'MISSING_CONTEXT' | 'OTHER'; comment?: string }, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<void>(`/evaluations/${encodeURIComponent(evaluationId)}/feedback`, {
      method: 'POST',
      body: jsonBody(request),
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },
};
