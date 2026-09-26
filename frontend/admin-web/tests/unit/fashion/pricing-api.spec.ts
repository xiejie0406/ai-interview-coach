import { beforeEach, describe, expect, it, vi } from 'vitest'
import { approveQuote, confirmQuote, getQuotePricing, requestQuoteApproval, saveQuotePricing } from '@/api/fashion/pricing'
import { fashionRequest } from '@/api/fashion/client'

vi.mock('@/api/fashion/client', () => ({ fashionRequest: vi.fn() }))
describe('Fashion pricing API', () => {
  beforeEach(() => vi.mocked(fashionRequest).mockReset())
  it('keeps every pricing write behind Java quote endpoints', async () => {
    vi.mocked(fashionRequest).mockResolvedValue({ code: 200, data: {} } as never)
    const body = { mode: 'combined' as const, taxMode: 'included' as const, feeTaxable: true,
      discountType: 'percent' as const, discountRate: '5.00', fixedDiscount: '0.00', freight: '300.00',
      validDays: 7, selectedComboIds: ['200'], lines: [{ detailId: '301', qty: 100, quotePrice: '80.00' }], rowVersion: 3 }
    await getQuotePricing('100'); await saveQuotePricing('100', body)
    await requestQuoteApproval('100', 'a'.repeat(64), '特殊商务条件', 4)
    await approveQuote('100', 'a'.repeat(64), '负责人确认', 5)
    await confirmQuote('100', 'a'.repeat(64), 6)
    expect(vi.mocked(fashionRequest).mock.calls.map(([request]) => request.path)).toEqual([
      'quotes/100/pricing', 'quotes/100/pricing', 'quotes/100/pricing/approval-request',
      'quotes/100/pricing/approval', 'quotes/100/pricing/confirm'
    ])
    expect(vi.mocked(fashionRequest).mock.calls[1][0]).toMatchObject({ method: 'put', body })
  })
})
