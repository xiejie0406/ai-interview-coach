import { beforeEach, describe, expect, it, vi } from 'vitest'

const { fashionRequestMock } = vi.hoisted(() => ({ fashionRequestMock: vi.fn() }))
vi.mock('@/api/fashion/client', () => ({ fashionRequest: fashionRequestMock }))

import { listProducts, updateProducts, updateProductStatus } from '@/api/fashion/product'

describe('Fashion product API', () => {
  beforeEach(() => fashionRequestMock.mockReset())

  it('查询只进入 Java 商品端点并传递取消信号', async () => {
    const signal = new AbortController().signal
    const params = { page: 1, pageSize: 20, keyword: '000123', status: '' as const }
    fashionRequestMock.mockResolvedValue({ code: 200, data: { items: [], total: 0 } })
    await listProducts(params, signal)
    expect(fashionRequestMock).toHaveBeenCalledWith({ path: 'products', params, signal })
  })

  it('批改和状态都携带 rowVersion', async () => {
    fashionRequestMock.mockResolvedValue({ code: 200, data: {} })
    await updateProducts([{ id: '42', rowVersion: 3, changes: { name: '新名称' } }])
    await updateProductStatus('42', 'inactive', 4)
    expect(fashionRequestMock).toHaveBeenNthCalledWith(1, {
      path: 'products/batch', method: 'put', body: { products: [{ id: '42', rowVersion: 3, changes: { name: '新名称' } }] }
    })
    expect(fashionRequestMock).toHaveBeenNthCalledWith(2, {
      path: 'products/42/status', method: 'put', body: { status: 'inactive', rowVersion: 4 }
    })
  })
})
