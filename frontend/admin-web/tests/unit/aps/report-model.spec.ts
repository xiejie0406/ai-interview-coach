import { describe, expect, it } from 'vitest'
import { dailyResultTag, decimalNumber, flattenOrderLines, percentText } from '@/views/aps/reports/report-model'
import type { ApsDailyTaskRow, ApsOrderDeliveryReport } from '@/types/aps/reporting'

describe('APS 报表视图模型', () => {
  it('兼容服务端 decimal 字符串且不把缺失达成率显示成 0%', () => {
    expect(decimalNumber('8.500000')).toBe(8.5)
    expect(percentText('0.8')).toBe('80.0%')
    expect(percentText(undefined)).toBe('—')
  })

  it('缺日冻结基线和延期使用不同风险标签', () => {
    const base = { reasonCodes: [], delayed: false, carryoverQty: '0' } as unknown as ApsDailyTaskRow
    expect(dailyResultTag(base)).toBe('success')
    expect(dailyResultTag({ ...base, reasonCodes: ['DAILY_BASELINE_MISSING'] })).toBe('info')
    expect(dailyResultTag({ ...base, delayed: true })).toBe('danger')
  })

  it('订单行展开后保留订单级 UNKNOWN，不伪造 ETA', () => {
    const report = {
      metadata: {},
      orders: [{
        orderId: 'order', orderNo: 'O-100', orderStatus: 'IN_PRODUCTION', etaState: 'UNKNOWN',
        productionCompleted: false, orderClosed: false, reasons: [],
        lines: [{ orderLineId: 'line', lineNo: 1, itemId: 'item', itemCode: 'A', itemName: '产品A',
          uomCode: 'PCS', demandQty: '100', releasedTerminalQty: '0', lineStatus: 'IN_PRODUCTION',
          etaState: 'UNKNOWN', reasons: [] }]
      }]
    } as unknown as ApsOrderDeliveryReport
    expect(flattenOrderLines(report)).toEqual([expect.objectContaining({
      orderNo: 'O-100', orderEtaState: 'UNKNOWN', etaState: 'UNKNOWN'
    })])
  })
})
