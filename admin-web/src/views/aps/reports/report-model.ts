import type {
  ApsDailyTaskRow,
  ApsOrderDeliveryReport,
  ApsOrderLineDeliveryRow,
  ApsReportDecimal
} from '@/types/aps/reporting'

export const decimalNumber = (value: ApsReportDecimal | undefined): number => {
  if (value === undefined) return 0
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

export const percentText = (value: ApsReportDecimal | undefined): string =>
  value === undefined ? '—' : `${(decimalNumber(value) * 100).toFixed(1)}%`

export const hourText = (value: ApsReportDecimal): string => `${decimalNumber(value).toFixed(2)} h`

export const dailyResultTag = (row: ApsDailyTaskRow): 'success' | 'warning' | 'danger' | 'info' => {
  if (row.reasonCodes.includes('DAILY_BASELINE_MISSING')) return 'info'
  if (row.delayed || row.reasonCodes.includes('NOT_IN_CURRENT_PLAN')) return 'danger'
  if (decimalNumber(row.carryoverQty) > 0) return 'warning'
  return 'success'
}

export interface FlatOrderLine extends ApsOrderLineDeliveryRow {
  readonly orderId: string
  readonly orderNo: string
  readonly orderStatus: string
  readonly orderEtaState: 'KNOWN' | 'UNKNOWN'
}

export const flattenOrderLines = (report: ApsOrderDeliveryReport): readonly FlatOrderLine[] =>
  report.orders.flatMap((order) => order.lines.map((line) => ({
    ...line,
    orderId: order.orderId,
    orderNo: order.orderNo,
    orderStatus: order.orderStatus,
    orderEtaState: order.etaState
  })))

export const localDateText = (date = new Date()): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
