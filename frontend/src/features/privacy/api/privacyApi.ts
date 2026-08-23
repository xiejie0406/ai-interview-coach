import { apiRequest, apiRequestWithMeta, jsonBody, type ApiResponse } from '../../../shared/api/client';

export type ConsentPurpose = 'SERVICE_TERMS' | 'PRIVACY_NOTICE' | 'VOICE_CAPTURE' | 'MODEL_PROCESSING';
export type PolicyView = { purpose: ConsentPurpose; versionId: string; title: string; summary: string; documentUrl: string; requiredForRegistration: boolean; revocable: boolean };
export type CurrentPoliciesView = { policySetVersion: string; policies: PolicyView[] };
export type ConsentView = { purpose: ConsentPurpose; policyVersionId: string; granted: boolean; recordedAt: string; revocable: boolean };

export type DataClassView = {
  code: string;
  classification: 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'HIGHLY_SENSITIVE';
  retentionDescription: string;
  deletionOwner: string;
};

export type PrivacyScope = 'PRACTICE' | 'INTERVIEW' | 'VOICE' | 'REPORT' | 'LEARNING' | 'BILLING_PROFILE' | 'ACCOUNT';
export type PrivacyScopeRequest = { scope: PrivacyScope; resourceId?: string | null };
export type ExportRequestView = { id: string; state: 'REQUESTED' | 'RUNNING' | 'READY' | 'FAILED_RETRYABLE' | 'FAILED_FINAL' | 'EXPIRED' | 'CANCELLED'; scope: PrivacyScopeRequest; requestedAt: string; expiresAt: string | null; downloadUrl: string | null };
export type DeletionPreflightView = { challengeId: string; confirmationPrompt: string; expiresAt: string; stepUpRequired: boolean; blockers: string[] };
export type DeletionRequestView = {
  id: string;
  state: 'REQUESTED' | 'VALIDATING' | 'HIDDEN' | 'DELETING_INTERNAL' | 'DELETING_EXTERNAL' | 'WAITING_RETENTION_EXPIRY' | 'COMPLETED' | 'BLOCKED_LEGAL' | 'PARTIAL_FAILED' | 'CANCELLED';
  scope: PrivacyScopeRequest;
  steps: Array<{ owner: string; state: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL_FAILED' | 'BLOCKED'; affectedCount?: number; reasonCode?: string | null }>;
  requestedAt: string;
  dueAt: string | null;
  cancellable: boolean;
  version: number;
};

export const privacyApi = {
  getCurrentPolicies(signal?: AbortSignal) {
    return apiRequest<CurrentPoliciesView>('/policies/current', { signal });
  },

  getConsents(signal?: AbortSignal) {
    return apiRequest<ConsentView[]>('/consents', { signal });
  },

  grantConsent(purpose: ConsentPurpose, policyVersionId: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<ConsentView>(`/consents/${purpose}/grants`, { method: 'POST', body: jsonBody({ policyVersionId, acknowledgement: true }), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },

  revokeConsent(purpose: ConsentPurpose, policyVersionId: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<ConsentView>(`/consents/${purpose}/revocations`, { method: 'POST', body: jsonBody({ policyVersionId, acknowledgement: true }), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },

  getDataInventory(signal?: AbortSignal) {
    return apiRequest<DataClassView[]>('/privacy/data-inventory', { signal });
  },

  requestExport(request: PrivacyScopeRequest, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<ExportRequestView>('/exports', { method: 'POST', body: jsonBody(request), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },

  getExport(id: string, signal?: AbortSignal) {
    return apiRequest<ExportRequestView>(`/exports/${encodeURIComponent(id)}`, { signal });
  },

  preflightDeletion(request: PrivacyScopeRequest, signal?: AbortSignal) {
    return apiRequest<DeletionPreflightView>('/deletion-requests/preflight', { method: 'POST', body: jsonBody(request), signal });
  },

  requestDeletion(request: PrivacyScopeRequest & { challengeId: string; confirmationResponse: string }, idempotencyKey: string, signal?: AbortSignal): Promise<ApiResponse<DeletionRequestView>> {
    return apiRequestWithMeta<DeletionRequestView>('/deletion-requests', { method: 'POST', body: jsonBody(request), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },

  getDeletionRequest(id: string, signal?: AbortSignal): Promise<ApiResponse<DeletionRequestView>> {
    return apiRequestWithMeta<DeletionRequestView>(`/deletion-requests/${encodeURIComponent(id)}`, { signal });
  },

  cancelDeletionRequest(id: string, etag: string, idempotencyKey: string, signal?: AbortSignal): Promise<ApiResponse<DeletionRequestView>> {
    return apiRequestWithMeta<DeletionRequestView>(`/deletion-requests/${encodeURIComponent(id)}/commands/cancel`, { method: 'POST', headers: { 'If-Match': etag, 'Idempotency-Key': idempotencyKey }, signal });
  },

  requestSensitiveAdminAccess(request: { targetClass: string; targetId: string; reasonCode: string; ticketRef: string }, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<{ id: string; state: 'DENIED' | 'APPROVED'; scopeHash: string; expiresAt: string | null }>('/admin/access-requests', { method: 'POST', body: jsonBody(request), headers: { 'Idempotency-Key': idempotencyKey }, signal });
  },
};
