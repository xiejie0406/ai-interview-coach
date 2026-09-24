import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fashionRequest } from '@/api/fashion/client'
import { downloadDeliveryArtifact, getDeliveryWorkspace, requestDeliveryFile, retryDeliveryFile } from '@/api/fashion/quoteFile'

vi.mock('@/api/fashion/client', () => ({ fashionRequest: vi.fn() }))

describe('Fashion delivery API', () => {
  beforeEach(() => vi.mocked(fashionRequest).mockReset())
  it('keeps generation retry and binary download behind Java quote endpoints', async () => {
    vi.mocked(fashionRequest).mockResolvedValue({ code: 200, data: {} } as never)
    await getDeliveryWorkspace('100')
    await requestDeliveryFile('100', 'pptx')
    await retryDeliveryFile('100', '200', 3)
    await downloadDeliveryArtifact('100', '200', 1)
    expect(vi.mocked(fashionRequest).mock.calls.map(([request]) => request.path)).toEqual([
      'quotes/100/files', 'quotes/100/files', 'quotes/100/files/200/retry', 'quotes/100/files/200/artifacts/1'
    ])
    expect(vi.mocked(fashionRequest).mock.calls[1][0]).toMatchObject({ method: 'post', body: {
      fileType: 'pptx', purpose: 'customer', rendererVersion: 'fashion-delivery-1.0'
    } })
    expect(vi.mocked(fashionRequest).mock.calls[3][0]).toMatchObject({ responseType: 'blob', timeoutMs: 120000 })
  })
})
