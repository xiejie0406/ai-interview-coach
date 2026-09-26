import { beforeEach, describe, expect, it, vi } from 'vitest'

const { fashionRequestMock } = vi.hoisted(() => ({ fashionRequestMock: vi.fn() }))
vi.mock('@/api/fashion/client', () => ({ fashionRequest: fashionRequestMock }))

import { adoptQuoteImage, cancelQuoteImageTask, createQuoteImageTask, reviewQuoteImage, uploadQuoteImage } from '@/api/fashion/image'
import type { QuoteImageTask, SelectionCombo } from '@/api/fashion/types'

describe('Fashion 图片任务 API', () => {
  beforeEach(() => {
    fashionRequestMock.mockReset()
    fashionRequestMock.mockResolvedValue({ code: 200, data: {} })
  })

  it('创建任务携带组合视觉摘要和幂等键', async () => {
    await createQuoteImageTask('100', {
      comboId: '200', imageType: 'model', sourceMode: 'sample', requestedCount: 2,
      parameters: { aspectRatio: '3:4', promptVersion: '1.0' }, requestKey: 'image-request-0001',
      quoteRowVersion: 3, comboRowVersion: 4, comboVisualHash: 'a'.repeat(64),
    })
    expect(fashionRequestMock).toHaveBeenCalledWith({
      path: 'quotes/100/images', method: 'post', body: expect.objectContaining({
        comboId: '200', requestKey: 'image-request-0001', quoteRowVersion: 3,
        comboRowVersion: 4, comboVisualHash: 'a'.repeat(64),
      }),
    })
  })

  it('上传、复核、采用和取消始终携带当前版本', async () => {
    const combo = { id: '200', rowVersion: 4, visualHash: 'a'.repeat(64) } as SelectionCombo
    const task = { id: '300', rowVersion: 7 } as QuoteImageTask
    const file = new File([new Uint8Array([1, 2, 3])], 'external.png', { type: 'image/png' })
    await uploadQuoteImage('100', combo, 3, 'model', 'upload-request-0001', file)
    await reviewQuoteImage('100', task, 1, 'reject', ['style'], 'wrong_style')
    await adoptQuoteImage('100', task, 1)
    await cancelQuoteImageTask('100', task)
    const upload = fashionRequestMock.mock.calls[0][0]
    expect(upload.body).toBeInstanceOf(FormData)
    expect(upload.params).toEqual({ comboId: '200', imageType: 'model', requestKey: 'upload-request-0001',
      quoteRowVersion: 3, comboRowVersion: 4, comboVisualHash: 'a'.repeat(64) })
    expect(fashionRequestMock.mock.calls[1][0].body).toEqual({
      resultNo: 1, decision: 'reject', checklist: ['style'], reason: 'wrong_style', rowVersion: 7,
    })
    expect(fashionRequestMock.mock.calls[2][0].params).toEqual({ rowVersion: 7 })
    expect(fashionRequestMock.mock.calls[3][0].params).toEqual({ rowVersion: 7 })
  })
})
