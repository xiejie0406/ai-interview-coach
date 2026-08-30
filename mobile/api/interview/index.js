import { interviewRequest, interviewRequestWithMeta, operationKey } from '@/utils/interviewRequest'

export function createInterviewPlan(data) {
  return interviewRequestWithMeta({
    url: '/interview-plans',
    method: 'POST',
    data,
    header: { 'Idempotency-Key': operationKey() }
  })
}

export function confirmInterviewPlan(planId, estimateVersion, etag) {
  return interviewRequestWithMeta({ url: `/interview-plans/${encodeURIComponent(planId)}/commands/confirm`, method: 'POST',
    data: { acknowledgedEstimateVersion: estimateVersion }, header: { 'If-Match': etag, 'Idempotency-Key': operationKey() } })
}

export function createInterview(data) {
  return interviewRequestWithMeta({ url: '/interviews', method: 'POST', data, header: { 'Idempotency-Key': operationKey() } })
}

export function getInterview(interviewId) {
  return interviewRequestWithMeta({ url: `/interviews/${encodeURIComponent(interviewId)}` })
}

export function startInterview(interviewId, etag) {
  return interviewRequestWithMeta({ url: `/interviews/${encodeURIComponent(interviewId)}/commands/start`, method: 'POST',
    header: { 'If-Match': etag, 'Idempotency-Key': operationKey() } })
}

export function submitInterviewAnswer(interviewId, etag, data) {
  return interviewRequestWithMeta({ url: `/interviews/${encodeURIComponent(interviewId)}/answers`, method: 'POST', data,
    header: { 'If-Match': etag, 'Idempotency-Key': operationKey() } })
}

export function applyInterviewCommand(interviewId, command, etag) {
  return interviewRequestWithMeta({ url: `/interviews/${encodeURIComponent(interviewId)}/commands/${command}`, method: 'POST', data: {},
    header: { 'If-Match': etag, 'Idempotency-Key': operationKey() } })
}

export function voicePreflight(interviewId, turnId, codecCandidates) {
  return interviewRequest({
    url: `/interviews/${encodeURIComponent(interviewId)}/voice-preflight`,
    method: 'POST',
    data: { turnId, codecCandidates }
  })
}

export function openVoiceSession(interviewId, turnId, codec, expectedSessionVersion) {
  return interviewRequest({
    url: `/interviews/${encodeURIComponent(interviewId)}/voice-sessions`, method: 'POST',
    data: { turnId, codec, expectedSessionVersion }, header: { 'Idempotency-Key': operationKey() }
  })
}

export function confirmTranscript(transcriptId, transcriptVersionId, transcriptVersion, correctedText) {
  return interviewRequest({
    url: `/transcripts/${encodeURIComponent(transcriptId)}/commands/confirm`, method: 'POST',
    data: { transcriptVersionId, correctedText, lowConfidenceAcknowledged: true },
    header: { 'If-Match': `"v${transcriptVersion}"`, 'Idempotency-Key': operationKey() }
  })
}

export function grantConsent(purpose, policyVersionId) {
  return interviewRequest({
    url: `/consents/${encodeURIComponent(purpose)}/grants`, method: 'POST',
    data: { policyVersionId, acknowledgement: true }, header: { 'Idempotency-Key': operationKey() }
  })
}
