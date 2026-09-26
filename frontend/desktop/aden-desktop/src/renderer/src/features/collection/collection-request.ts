import type { CollectionAction, CollectionOperation } from '../../../../shared/contracts/collection'
import type { EpochContext } from '../../../../shared/contracts/operator-events'

/** Vue 的 ref/reactive 深层数组也是 Proxy。跨 contextBridge 前递归复制为纯 JSON DTO。 */
export function collectionRequest(context: EpochContext, action: CollectionAction, input: Record<string, unknown>): CollectionOperation {
  return JSON.parse(JSON.stringify({ workspaceId: context.workspaceId, sessionEpoch: context.sessionEpoch,
    workspaceEpoch: context.workspaceEpoch, action, input })) as CollectionOperation
}
