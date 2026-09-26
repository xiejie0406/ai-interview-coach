import { beforeEach, describe, expect, it, vi } from 'vitest'

const { fashionRequestMock } = vi.hoisted(() => ({
  fashionRequestMock: vi.fn()
}))

vi.mock('@/api/fashion/client', () => ({
  fashionRequest: fashionRequestMock
}))

import { listFashionSettings, updateFashionSetting } from '@/api/fashion/settings'

describe('Fashion settings API', () => {
  beforeEach(() => fashionRequestMock.mockReset())

  it('只通过 Java /fashion/settings 读取白名单设置', async () => {
    const controller = new AbortController()
    fashionRequestMock.mockResolvedValue({ code: 200, data: [] })

    await listFashionSettings(controller.signal)

    expect(fashionRequestMock).toHaveBeenCalledWith({
      path: 'settings',
      signal: controller.signal
    })
  })

  it('更新请求只携带已选择的 key 与 value', async () => {
    fashionRequestMock.mockResolvedValue({ code: 200, data: {} })

    await updateFashionSetting('fashion.stock.freshnessHours', '24')

    expect(fashionRequestMock).toHaveBeenCalledWith({
      path: 'settings',
      method: 'put',
      body: {
        key: 'fashion.stock.freshnessHours',
        value: '24'
      }
    })
  })
})
