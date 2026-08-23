import { apiRequestWithMeta, jsonBody, type ApiResponse } from '../../../shared/api/client';

export type InterviewMode = 'TEXT' | 'CASCADE_VOICE';
export type InterviewTargetRole = 'JAVA_BACKEND' | 'AI_APPLICATION' | 'AGENT_ENGINEER';
export type InterviewLevel = 'JUNIOR' | 'MID' | 'SENIOR';

export type InterviewSetup = {
  targetRole: InterviewTargetRole;
  targetLevel: InterviewLevel;
  topics: string[];
  durationMinutes: number;
  mode: InterviewMode;
};

export type InterviewPlanView = {
  id: string;
  planVersionNo: number;
  state: 'DRAFT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
  mode: InterviewMode;
  questionCount: number;
  followUpBudget: number;
  estimatedUsage: { unit: 'TEXT_SESSION' | 'VOICE_SECOND'; quantity: number; estimateVersion: string };
  reservationId?: string | null;
  expiresAt: string;
  version: number;
};

export type InterviewState = 'READY' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETING' | 'COMPLETED' | 'CANCELLED' | 'FAILED_RECOVERABLE' | 'FAILED_FINAL';
export type InterviewCommand = 'start' | 'pause' | 'resume' | 'skip' | 'complete' | 'cancel';
export type AllowedInterviewCommand = Uppercase<InterviewCommand> | 'SUBMIT_ANSWER' | 'RECOVER';

export type InterviewSnapshot = {
  id: string;
  planId: string;
  planVersionNo: number;
  planContentHash: string;
  mode: InterviewMode;
  state: InterviewState;
  turns: InterviewTurn[];
  lastStableTurnSequence: number;
  pendingJobIds: string[];
  reservation: { id: string; state: 'RESERVED' | 'SETTLED' | 'RELEASED' | 'EXPIRED' };
  report: { id: string | null; state: 'NOT_REQUESTED' | 'PENDING' | 'RUNNING' | 'READY' | 'PARTIAL' | 'FAILED' | 'CANCELLED' };
  voiceSummary: null | { executionId: string; state: 'IDLE' | 'LISTENING' | 'TRANSCRIBING' | 'CONFIRMING' | 'THINKING' | 'SPEAKING' | 'DEGRADED' | 'CANCELLED'; transcriptId: string | null; audioArtifactId: string | null };
  allowedCommands: AllowedInterviewCommand[];
  streamCursor?: string | null;
  recoveryExpiresAt: string | null;
  failureCode: string | null;
  version: number;
};

export type InterviewTurn = {
  turnId: string;
  sequence: number;
  kind: 'PRIMARY' | 'FOLLOW_UP' | 'CLARIFICATION';
  state: 'PLANNED' | 'QUESTION_COMMITTED' | 'ANSWER_CONFIRMED' | 'CLOSED' | 'SKIPPED' | 'CANCELLED' | 'FAILED';
  questionText?: string | null;
  answerVersionId?: string | null;
};

export type OperationAccepted = { operationId: string; statusUrl: string };

export type InterviewQuestion = {
  turnId: string;
  turnSequence: number;
  text: string;
};

export type InterviewEventEnvelope = {
  eventId: string;
  type: string;
  streamId: string;
  aggregateId: string;
  aggregateVersion: number;
  sequence: number;
  occurredAt: string;
  schemaVersion: number;
  correlationId: string;
  durability: 'DURABLE' | 'EPHEMERAL';
  data: Record<string, unknown>;
};

export const interviewApi = {
  createPlan(setup: InterviewSetup, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<InterviewPlanView>('/interview-plans', {
      method: 'POST',
      body: jsonBody(setup),
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  getPlan(planId: string, signal?: AbortSignal): Promise<ApiResponse<InterviewPlanView>> {
    return apiRequestWithMeta<InterviewPlanView>(`/interview-plans/${encodeURIComponent(planId)}`, { signal });
  },

  applyPlanCommand(planId: string, command: 'confirm' | 'cancel', acknowledgedEstimateVersion: string, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<InterviewPlanView>(`/interview-plans/${encodeURIComponent(planId)}/commands/${command}`, {
      method: 'POST',
      body: jsonBody({ acknowledgedEstimateVersion }),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  createSession(confirmedPlanId: string, planVersionNo: number, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<InterviewSnapshot>('/interviews', {
      method: 'POST',
      body: jsonBody({ confirmedPlanId, planVersionNo }),
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  recoverSession(interviewId: string, signal?: AbortSignal): Promise<ApiResponse<InterviewSnapshot>> {
    return apiRequestWithMeta<InterviewSnapshot>(`/interviews/${encodeURIComponent(interviewId)}`, { signal });
  },

  applyCommand(interviewId: string, command: InterviewCommand, etag: string, idempotencyKey: string, reasonCode?: string, signal?: AbortSignal) {
    return apiRequestWithMeta<InterviewSnapshot | OperationAccepted>(`/interviews/${encodeURIComponent(interviewId)}/commands/${command}`, {
      method: 'POST',
      body: jsonBody(reasonCode ? { reasonCode } : {}),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  submitAnswer(interviewId: string, request: { turnId: string; turnSequence: number; text?: string; confirmedTranscriptVersionId?: string }, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequestWithMeta<OperationAccepted>(`/interviews/${encodeURIComponent(interviewId)}/answers`, {
      method: 'POST',
      body: jsonBody(request),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },
};
