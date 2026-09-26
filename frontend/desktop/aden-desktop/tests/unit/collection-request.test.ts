import { describe, expect, it } from 'vitest'
import { reactive, ref } from 'vue'
import { collectionRequest } from '../../src/renderer/src/features/collection/collection-request'
import type { CollectionAction } from '../../src/shared/contracts/collection'

describe('Collection renderer structured-clone boundary', () => {
  it('copies nested Vue curation arrays and overrides into a cloneable DTO', () => {
    const state = ref({ revision: 3, notes: '人工备注', tags: ['已检查'], overrides: { price: '99.9900' }, removedImageIds: ['image-1'], imageOrder: ['image-2', 'image-1'], mainImageId: 'image-2' })
    const input = { itemId: 'item-1', curation: { ...state.value } }
    // 精确复现安装版故障：浅展开仍保留嵌套 Proxy，Electron 的 structured clone 拒绝。
    expect(() => structuredClone(input)).toThrow()
    const operation = collectionRequest({ workspaceId: 'workspace-1', sessionEpoch: 1, workspaceEpoch: 2 }, 'curation', input)
    const copied = structuredClone(operation)
    expect(copied.input).toEqual({ itemId: 'item-1', curation: { revision: 3, notes: '人工备注', tags: ['已检查'], overrides: { price: '99.9900' }, removedImageIds: ['image-1'], imageOrder: ['image-2', 'image-1'], mainImageId: 'image-2' } })
    state.value.removedImageIds.push('later'); state.value.overrides.price = '1.00'
    expect((copied.input.curation as typeof state.value).removedImageIds).toEqual(['image-1'])
    expect((copied.input.curation as typeof state.value).overrides.price).toBe('99.9900')
  })
  it.each<CollectionAction>(['list', 'detail', 'versions', 'snapshot', 'create', 'curation', 'trash', 'restore', 'image', 'upload', 'export'])('all %s calls detach reactive payloads before IPC', action => {
    const context = reactive({ workspaceId: 'workspace-1', sessionEpoch: 1, workspaceEpoch: 2 })
    const input = reactive({ itemIds: ['one', 'two'], nested: { fields: ['标题'] } })
    const operation = collectionRequest(context, action, input)
    expect(() => structuredClone(operation)).not.toThrow()
    expect(operation).toEqual({ ...context, action, input })
  })
})
