import request from '@/utils/request'
import type {
  ApsAdvanceExecutionPhaseInput,
  ApsChangeExecutionResourceInput,
  ApsCorrectProductionReportInput,
  ApsCreateExecutionRunInput,
  ApsCreateProductionReportInput,
  ApsExecutionRunDetail,
  ApsQualityDecisionInput,
  ApsQuantityMovementInput,
  ApsTransitionExecutionInput
} from '@/types/aps/execution'

const runs = '/api/aps/v1/execution-runs'

export const createExecutionRun = (idempotencyKey: string, data: ApsCreateExecutionRunInput) => request({
  url: runs,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>

export const getExecutionRun = (executionRunId: string) =>
  request({ url: `${runs}/${executionRunId}`, method: 'get' }) as Promise<ApsExecutionRunDetail>

export const transitionExecutionRun = (executionRunId: string, data: ApsTransitionExecutionInput) => request({
  url: `${runs}/${executionRunId}/transitions`, method: 'post', data
}) as Promise<ApsExecutionRunDetail>

export const changeExecutionResource = (
  executionRunId: string,
  idempotencyKey: string,
  data: ApsChangeExecutionResourceInput
) => request({
  url: `${runs}/${executionRunId}/resource-changes`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>

export const advanceExecutionPhase = (
  executionRunId: string,
  idempotencyKey: string,
  data: ApsAdvanceExecutionPhaseInput
) => request({
  url: `${runs}/${executionRunId}/phase-advances`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>

export const createProductionReport = (
  executionRunId: string,
  idempotencyKey: string,
  data: ApsCreateProductionReportInput
) => request({
  url: `${runs}/${executionRunId}/reports`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>

export const correctProductionReport = (
  reportId: string,
  idempotencyKey: string,
  data: ApsCorrectProductionReportInput
) => request({
  url: `/api/aps/v1/production-reports/${reportId}/corrections`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>

export const decideOutputLotQuality = (
  outputLotId: string,
  idempotencyKey: string,
  data: ApsQualityDecisionInput
) => request({
  url: `/api/aps/v1/output-lots/${outputLotId}/quality-decisions`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>

export const moveOutputLotQuantity = (
  outputLotId: string,
  idempotencyKey: string,
  data: ApsQuantityMovementInput
) => request({
  url: `/api/aps/v1/output-lots/${outputLotId}/quantity-movements`,
  method: 'post',
  headers: { 'Idempotency-Key': idempotencyKey },
  data
}) as Promise<ApsExecutionRunDetail>
