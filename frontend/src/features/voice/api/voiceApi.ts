import { apiRequest, jsonBody } from '../../../shared/api/client';

export type VoiceCodec = 'audio/webm;codecs=opus' | 'audio/ogg;codecs=opus' | 'audio/wav';

export type VoicePreflight = {
  enabled: boolean;
  consentRequired: boolean;
  supportedCodecs: string[];
  maxDurationSeconds: number;
  maxBytes: number;
  unavailableReasonCode: string | null;
};

export type VoiceSessionHandle = {
  voiceSessionId: string;
  voiceExecutionId: string;
  inputArtifactId: string;
  websocketPath: string;
  socketTicket: string;
  expiresAt: string;
  codec: string;
  protocolVersion: 1;
  socketGeneration: number;
  initialServerSequence: number;
  maxInFlightChunks: number;
  maxChunkBytes: number;
  maxBufferedDurationMs: number;
  maxDurationSeconds: number;
  maxBytes: number;
};

export type AudioArtifactView = {
  id: string;
  state: 'CREATED' | 'UPLOADING' | 'UPLOADED' | 'TRANSCRIBING' | 'TRANSCRIBED' | 'SYNTHESIZING' | 'SYNTHESIZED' | 'DELETE_QUEUED' | 'DELETED' | 'UPLOAD_FAILED' | 'TRANSCRIBE_FAILED' | 'SYNTHESIS_FAILED' | 'DELETE_PARTIAL';
  purpose: 'ANSWER_TRANSCRIPTION' | 'TTS_PLAYBACK';
  expiresAt: string;
  codec: string | null;
  bytes: number | null;
  durationMillis: number | null;
  failureCode: string | null;
  deleteStatus?: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'PARTIAL_FAILED' | null;
  version: number;
};

export type TranscriptView = {
  id: string;
  state: 'OPEN' | 'ASR_FINAL' | 'CONFIRMED' | 'CANCELLED';
  sessionId: string;
  turnId: string;
  audioArtifactId: string;
  latestVersion: null | { id: string; versionNo: number; source: 'ASR' | 'USER_CORRECTION'; text: string; language: string; offsetUnit: 'UTF16' | 'UNICODE_CODE_POINT'; lowConfidenceSpans: Array<{ startInclusive: number; endExclusive: number; confidence: number }>; createdAt: string };
  confirmedVersionId: string | null;
  version: number;
};

export type ConfirmedTranscriptView = { transcriptId: string; confirmedTranscriptVersionId: string; answerVersionId: string; nextStepOperation: { operationId: string; jobId?: string | null; statusUrl: string }; version: number };

export const voiceApi = {
  preflight(interviewId: string, turnId: string, codecCandidates: VoiceCodec[], signal?: AbortSignal) {
    return apiRequest<VoicePreflight>(`/interviews/${encodeURIComponent(interviewId)}/voice-preflight`, {
      method: 'POST',
      body: jsonBody({ turnId, codecCandidates }),
      signal,
    });
  },

  openSession(interviewId: string, turnId: string, codec: string, expectedSessionVersion: number, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<VoiceSessionHandle>(`/interviews/${encodeURIComponent(interviewId)}/voice-sessions`, {
      method: 'POST',
      body: jsonBody({ turnId, codec, expectedSessionVersion }),
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  confirmTranscript(transcriptId: string, request: { transcriptVersionId: string; correctedText?: string; lowConfidenceAcknowledged?: boolean }, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<ConfirmedTranscriptView>(`/transcripts/${encodeURIComponent(transcriptId)}/commands/confirm`, {
      method: 'POST',
      body: jsonBody(request),
      headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  getArtifact(artifactId: string, signal?: AbortSignal) {
    return apiRequest<AudioArtifactView>(`/audio-artifacts/${encodeURIComponent(artifactId)}`, { signal });
  },

  getTranscript(transcriptId: string, signal?: AbortSignal) {
    return apiRequest<TranscriptView>(`/transcripts/${encodeURIComponent(transcriptId)}`, { signal });
  },

  deleteArtifact(artifactId: string, etag: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<AudioArtifactView>(`/audio-artifacts/${encodeURIComponent(artifactId)}/commands/delete`, { method: 'POST', headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey }, signal });
  },
};
