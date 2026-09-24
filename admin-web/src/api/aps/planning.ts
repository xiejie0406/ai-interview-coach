import request from '@/utils/request'
import type {
  ApsCreatePlanLockInput,
  ApsCreateAdjustmentInput,
  ApsAdjustmentAccepted,
  ApsCreateStructuralAdjustmentInput,
  ApsStructuralAdjustmentAccepted,
  ApsPublishPlanInput,
  ApsPlanPublished,
  ApsDiscardCandidateInput,
  ApsPlanCandidateDiscarded,
  ApsPlanVersionComparison,
  ApsPlanVersionDetail
} from '@/types/aps/planning'

const planVersions = '/api/aps/v1/plan-versions'

export const getPlanVersionDetail = (planVersionId: string) =>
  request({ url: `${planVersions}/${planVersionId}`, method: 'get' }) as Promise<ApsPlanVersionDetail>

export const comparePlanVersion = (planVersionId: string, baseVersionId?: string) =>
  request({
    url: `${planVersions}/${planVersionId}/comparison`,
    method: 'get',
    params: baseVersionId ? { baseVersionId } : undefined
  }) as Promise<ApsPlanVersionComparison>

export const createPlanLock = (planVersionId: string, data: ApsCreatePlanLockInput) =>
  request({ url: `${planVersions}/${planVersionId}/locks`, method: 'post', data }) as Promise<ApsPlanVersionDetail>

export const deletePlanLock = (
  planVersionId: string,
  lockId: string,
  expectedPlanRowVersion: number,
  expectedLockRowVersion: number
) => request({
  url: `${planVersions}/${planVersionId}/locks/${lockId}`,
  method: 'delete',
  params: { expectedPlanRowVersion, expectedLockRowVersion }
}) as Promise<ApsPlanVersionDetail>

export const createPlanAdjustment = (
  planVersionId: string,
  idempotencyKey: string,
  data: ApsCreateAdjustmentInput
) => request({
  url: `${planVersions}/${planVersionId}/adjustments`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsAdjustmentAccepted>

export const createPlanStructuralAdjustment = (
  planVersionId: string,
  idempotencyKey: string,
  data: ApsCreateStructuralAdjustmentInput
) => request({
  url: `${planVersions}/${planVersionId}/structural-adjustments`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsStructuralAdjustmentAccepted>

export const publishPlanVersion = (planVersionId: string, data: ApsPublishPlanInput) =>
  request({ url: `${planVersions}/${planVersionId}/publish`, method: 'post', data }) as Promise<ApsPlanPublished>

export const discardPlanCandidate = (planVersionId: string, data: ApsDiscardCandidateInput) =>
  request({ url: `${planVersions}/${planVersionId}/discard`, method: 'post', data }) as Promise<ApsPlanCandidateDiscarded>
