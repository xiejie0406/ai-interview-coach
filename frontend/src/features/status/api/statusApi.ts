import { apiRequest } from '../../../shared/api/client';

export type ServiceHealth = { status: 'UP' | 'DEGRADED'; service: 'ai-interview-coach'; version: string };
export type PublicStatus = { status: 'OPERATIONAL' | 'DEGRADED' | 'MAINTENANCE'; updatedAt: string; message?: string | null };

export const statusApi = {
  getHealth(signal?: AbortSignal) {
    return apiRequest<ServiceHealth>('/health', { signal });
  },

  getPublicStatus(signal?: AbortSignal) {
    return apiRequest<PublicStatus>('/status', { signal });
  },
};
