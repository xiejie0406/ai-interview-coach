import { apiRequest, jsonBody } from '../../../shared/api/client';

export type PlanView = {
  planId: string;
  priceVersionId: string;
  code: string;
  currency: string;
  currencyExponent: number;
  amountMinor: number;
  entitlements: Array<{ capability: string; quantity: number; unit: string }>;
};

export type EntitlementView = {
  capability: string;
  granted: boolean;
  remaining: number;
  unit: string;
  source: 'FREE' | 'PROMOTION' | 'PURCHASE' | 'ADMIN_GRANT';
};

export type OrderView = {
  id: string;
  state: 'DRAFT' | 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'REFUND_PENDING' | 'REFUNDED' | 'FAILED';
  priceVersionId: string;
  currency: string;
  currencyExponent: number;
  amountMinor: number;
  checkoutUrl?: string | null;
  version: number;
};

export type UsageView = {
  projectionVersion: number;
  asOf: string;
  reservations: Array<{ reservationId: string; businessOperationId: string; capability: string; quantity: number; unit: string; state: 'RESERVED' | 'SETTLED' | 'RELEASED' | 'EXPIRED'; createdAt: string; expiresAt: string }>;
  settlements: Array<{ settlementId: string; reservationId: string; actualQuantity: number; unit: string; settledAt: string }>;
  releases: Array<{ releaseId: string; reservationId: string; releasedQuantity: number; unit: string; reasonCode: string; releasedAt: string }>;
};

export const billingApi = {
  listPlans(signal?: AbortSignal) {
    return apiRequest<PlanView[]>('/plans', { signal });
  },

  getEntitlements(signal?: AbortSignal) {
    return apiRequest<EntitlementView[]>('/entitlements', { signal });
  },

  getUsage(signal?: AbortSignal) {
    return apiRequest<UsageView>('/usage', { signal });
  },

  createOrder(priceVersionId: string, idempotencyKey: string, signal?: AbortSignal) {
    return apiRequest<OrderView>('/orders', {
      method: 'POST',
      body: jsonBody({ priceVersionId, acknowledgement: true }),
      headers: { 'Idempotency-Key': idempotencyKey },
      signal,
    });
  },

  getOrder(orderId: string, signal?: AbortSignal) {
    return apiRequest<OrderView>(`/orders/${encodeURIComponent(orderId)}`, { signal });
  },
};
