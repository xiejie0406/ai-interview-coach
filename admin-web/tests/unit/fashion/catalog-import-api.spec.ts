import { beforeEach, describe, expect, it, vi } from 'vitest'

const { fashionRequestMock } = vi.hoisted(() => ({ fashionRequestMock: vi.fn() }))
vi.mock('@/api/fashion/client', () => ({ fashionRequest: fashionRequestMock, FASHION_API_PREFIX: '/fashion' }))

import {
  catalogTemplateUrl,
  previewCatalogImport,
  publishCatalogImport,
  restoreCatalogImport
} from '@/api/fashion/catalogImport'

describe('Fashion strict catalog import API', () => {
  beforeEach(() => fashionRequestMock.mockReset())

  it('价格预览只提交范围、文件和业务时间', async () => {
    fashionRequestMock.mockResolvedValue({ code: 200, data: {} })
    await previewCatalogImport({
      kind: 'price', file: new File(['price'], 'price.csv'), sourceCode: 'MANUAL',
      categoryCode: 'TOP', asOf: '2026-09-13T00:00:00Z'
    })
    const call = fashionRequestMock.mock.calls[0][0]
    expect(call.path).toBe('imports/prices/preview')
    expect(call.body).toBeInstanceOf(FormData)
    expect(call.body.get('categoryCode')).toBe('TOP')
    expect(call.body.get('warehouseCode')).toBeNull()
    expect(catalogTemplateUrl('price')).toMatch(/\/fashion\/imports\/prices\/template$/)
  })

  it('库存发布携带版本且恢复创建新请求', async () => {
    fashionRequestMock.mockResolvedValue({ code: 200, data: {} })
    await previewCatalogImport({
      kind: 'stock', file: new File(['stock'], 'stock.csv'), sourceCode: 'MANUAL',
      warehouseCode: 'MAIN', asOf: '2026-09-13T00:00:00Z'
    })
    expect(fashionRequestMock.mock.calls[0][0].body.get('warehouseCode')).toBe('MAIN')
    await publishCatalogImport('stock', '101', 3)
    expect(fashionRequestMock).toHaveBeenLastCalledWith({
      path: 'imports/stocks/101/publish', method: 'post', body: { rowVersion: 3 }, timeoutMs: 120_000
    })
    await restoreCatalogImport('stock', '101', 'restore-1', '恢复显式零')
    expect(fashionRequestMock).toHaveBeenLastCalledWith({
      path: 'imports/stocks/101/restore', method: 'post',
      body: { requestKey: 'restore-1', operatorNote: '恢复显式零' }, timeoutMs: 120_000
    })
  })
})
