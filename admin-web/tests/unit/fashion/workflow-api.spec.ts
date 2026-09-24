import { beforeEach, describe, expect, it, vi } from 'vitest'

const { fashionRequestMock } = vi.hoisted(() => ({ fashionRequestMock: vi.fn() }))
vi.mock('@/api/fashion/client', () => ({ fashionRequest: fashionRequestMock }))

import { archiveCustomer } from '@/api/fashion/customer'
import { closeQuote, updateQuote } from '@/api/fashion/quote'
import {
  applyRequirementRun,
  applyProductAttributeRun,
  cancelRun,
  createProductAttributeRun,
  createRequirementRun,
  publishAgentVersion
} from '@/api/fashion/agent'
import { applySelectionRun, createSelectionRun, replaceComboCandidate, updateComboLocks } from '@/api/fashion/selection'
import type { SelectionCombo } from '@/api/fashion/types'

describe('Fashion customer, quote and Agent API', () => {
  beforeEach(() => {
    fashionRequestMock.mockReset()
    fashionRequestMock.mockResolvedValue({ code: 200, data: {} })
  })

  it('客户归档、方案更新和关闭都携带乐观锁版本', async () => {
    await archiveCustomer('101', 3)
    await updateQuote('201', {
      customerId: '101', title: '秋季员工服', requirements: { preferredColors: [], exclusions: [] },
      requestedQty: 100, budget: 30000, budgetBasis: 'total', quoteMode: 'alternatives',
      progressive: true, tiers: [{ count: 1, slots: ['TOP'], candidateCount: 3 }],
      warehouseCode: 'MAIN', rowVersion: 5
    })
    await closeQuote('201', 6)
    expect(fashionRequestMock).toHaveBeenNthCalledWith(1, {
      path: 'customers/101/archive', method: 'put', body: { rowVersion: 3 }
    })
    expect(fashionRequestMock.mock.calls[1][0].body.rowVersion).toBe(5)
    expect(fashionRequestMock).toHaveBeenNthCalledWith(3, {
      path: 'quotes/201/close', method: 'put', body: { rowVersion: 6 }
    })
  })

  it('Run 创建、取消、采用和 Agent 发布使用独立权限端点', async () => {
    await createRequirementRun('201', '需要一百套秋季员工服装', 'requirement-request-0001')
    await cancelRun('301', 7)
    await applyRequirementRun('301', 'apply-request-0001', 8)
    await publishAgentVersion('401', '402', 2)
    expect(fashionRequestMock).toHaveBeenNthCalledWith(1, {
      path: 'ai/runs/requirement-analysis', method: 'post',
      body: { quoteId: '201', sourceText: '需要一百套秋季员工服装', requestKey: 'requirement-request-0001' }
    })
    expect(fashionRequestMock).toHaveBeenNthCalledWith(2, {
      path: 'ai/runs/301/cancel', method: 'put', body: { rowVersion: 7 }
    })
    expect(fashionRequestMock).toHaveBeenNthCalledWith(3, {
      path: 'ai/runs/301/apply-requirement', method: 'post',
      body: { requestKey: 'apply-request-0001', quoteRowVersion: 8 }
    })
    expect(fashionRequestMock).toHaveBeenNthCalledWith(4, {
      path: 'ai/agents/401/versions/402/publish', method: 'post', body: { rowVersion: 2 }
    })
  })

  it('商品属性建议 Run 与人工采用使用独立端点和商品版本', async () => {
    await createProductAttributeRun('501', 'product-attribute-request-0001')
    await applyProductAttributeRun('601', 'product-attribute-apply-0001', 9)
    expect(fashionRequestMock).toHaveBeenNthCalledWith(1, {
      path: 'ai/runs/product-attribute-suggestion', method: 'post',
      body: { productId: '501', requestKey: 'product-attribute-request-0001' }
    })
    expect(fashionRequestMock).toHaveBeenNthCalledWith(2, {
      path: 'ai/runs/601/apply-product-attributes', method: 'post',
      body: { requestKey: 'product-attribute-apply-0001', productRowVersion: 9 }
    })
  })

  it('选品生成、采用、锁定和替换都携带冻结版本摘要', async () => {
    const combo = {
      id: '701', quoteId: '201', comboNo: 'C-701', name: '两品类', categoryCount: 2,
      setQty: 100, selected: true, sortNo: 0, lockedSlots: ['SLOT-1'],
      visualHash: 'a'.repeat(64), rowVersion: 4, details: []
    } as SelectionCombo
    await createSelectionRun('201', 'selection-request-0001', combo.id, combo.visualHash)
    await applySelectionRun('801', 'selection-apply-0001', 6, { 'tier-2': 'b'.repeat(64) })
    await updateComboLocks('201', combo, 6, ['SLOT-1', 'SLOT-2'])
    await replaceComboCandidate('201', combo, 6, 'SLOT-2', 'CAND-100')
    expect(fashionRequestMock.mock.calls[0][0]).toEqual({
      path: 'ai/runs/selection-styling', method: 'post',
      body: { quoteId: '201', requestKey: 'selection-request-0001', baseComboId: '701', comboVisualHash: 'a'.repeat(64) }
    })
    expect(fashionRequestMock.mock.calls[1][0].body).toEqual({
      requestKey: 'selection-apply-0001', quoteRowVersion: 6, comboVisualHashes: { 'tier-2': 'b'.repeat(64) }
    })
    expect(fashionRequestMock.mock.calls[2][0].body).toEqual({
      lockedSlots: ['SLOT-1', 'SLOT-2'], quoteRowVersion: 6,
      comboRowVersion: 4, visualHash: 'a'.repeat(64)
    })
    expect(fashionRequestMock.mock.calls[3][0].body).toEqual({
      slotCode: 'SLOT-2', candidateRef: 'CAND-100', quoteRowVersion: 6,
      comboRowVersion: 4, comboVisualHash: 'a'.repeat(64)
    })
  })
})
