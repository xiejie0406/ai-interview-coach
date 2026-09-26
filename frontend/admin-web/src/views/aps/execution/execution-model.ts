import type {
  ApsExecutionAction,
  ApsExecutionRunStatus,
  ApsOutputLot,
  ApsReportQuantities
} from '@/types/aps/execution'

const actions: Record<ApsExecutionRunStatus, readonly ApsExecutionAction[]> = {
  READY: ['START', 'CANCEL'],
  RUNNING: ['PAUSE'],
  PAUSED: ['RESUME', 'CANCEL'],
  WAIT_QUALITY: [],
  COMPLETED: [],
  CANCELLED: []
}

export const allowedExecutionActions = (status: ApsExecutionRunStatus) => actions[status]

export function validateReportQuantities(value: ApsReportQuantities): string | undefined {
  const values = Object.values(value)
  if (values.some((quantity) => !Number.isFinite(quantity) || quantity < 0)) return '所有数量必须是非负数'
  if (value.processedQty !== value.goodQty + value.pendingQty + value.rejectedQty) {
    return '本次加工量必须等于合格量、待检量和不良量之和'
  }
  if (value.scrapQty > value.rejectedQty) return '报废量不能超过不良量'
  if (value.transferredQty > value.goodQty) return '转序量不能超过合格量'
  return undefined
}

export function availableQualityQuantity(lot: ApsOutputLot): number {
  if (lot.qualityStatus === 'PENDING' || lot.qualityStatus === 'HOLD') {
    return Math.max(0, lot.totalQty - lot.availableQty - lot.reservedQty - lot.consumedQty - lot.scrappedQty)
  }
  return lot.availableQty + lot.reservedQty
}

export function isConcurrencyConflict(error: unknown): boolean {
  if (typeof error !== 'object' || error == null) return false
  const response = (error as { response?: { status?: number; data?: { code?: string } } }).response
  return response?.status === 409 || ['STALE_VERSION', 'STALE_INPUT'].includes(response?.data?.code ?? '')
}
