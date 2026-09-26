import { describe, expect, it } from 'vitest'
import { allowedExecutionActions, availableQualityQuantity, isConcurrencyConflict, validateReportQuantities } from '@/views/aps/execution/execution-model'
import type { ApsOutputLot } from '@/types/aps/execution'

describe('APS 现场执行页面模型', () => {
  it('只开放状态机允许的动作', () => {
    expect(allowedExecutionActions('READY')).toEqual(['START', 'CANCEL'])
    expect(allowedExecutionActions('RUNNING')).toEqual(['PAUSE'])
    expect(allowedExecutionActions('WAIT_QUALITY')).toEqual([])
  })

  it('在客户端给出报工数量的即时说明', () => {
    expect(validateReportQuantities({ processedQty: 10, goodQty: 8, pendingQty: 1, rejectedQty: 1, scrapQty: 1, transferredQty: 3 })).toBeUndefined()
    expect(validateReportQuantities({ processedQty: 10, goodQty: 8, pendingQty: 0, rejectedQty: 1, scrapQty: 1, transferredQty: 0 })).toContain('必须等于')
    expect(validateReportQuantities({ processedQty: 10, goodQty: 8, pendingQty: 1, rejectedQty: 1, scrapQty: 2, transferredQty: 0 })).toContain('报废量')
  })

  it('区分并发冲突并计算仍可处置数量', () => {
    expect(isConcurrencyConflict({ response: { status: 409 } })).toBe(true)
    expect(isConcurrencyConflict({ response: { status: 500 } })).toBe(false)
    const lot = { qualityStatus: 'PENDING', totalQty: 20, availableQty: 0, reservedQty: 0, consumedQty: 0, scrappedQty: 4 } as ApsOutputLot
    expect(availableQualityQuantity(lot)).toBe(16)
  })
})
