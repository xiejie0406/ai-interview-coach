import type { ApsRouteEdge, ApsRouteNode, ApsValidationIssue, DependencyType } from '@/types/aps/manufacturing'

export interface EditableEdge {
  id?: string
  predecessorNodeCode: string
  successorNodeCode: string
  dependencyType: DependencyType
  thresholdQty?: number
  thresholdRatio?: number
  transferBatchQty?: number
  lagSeconds: number
  consumesOutput: boolean
}

export function validateRouteGraph(nodes: Array<Pick<ApsRouteNode, 'id' | 'nodeCode'>>, edges: EditableEdge[], forPublish = false): ApsValidationIssue[] {
  const issues: ApsValidationIssue[] = []
  const codes = new Set<string>()
  for (const node of nodes) {
    const code = node.nodeCode.trim().toUpperCase()
    if (codes.has(code)) issues.push(issue('DUPLICATE_NODE_CODE', 'ROUTE_NODE', node.id, 'nodeCode', `节点编码重复：${code}`))
    codes.add(code)
  }
  const outgoing = new Map<string, Set<string>>()
  const indegree = new Map<string, number>()
  codes.forEach(code => { outgoing.set(code, new Set()); indegree.set(code, 0) })
  for (const edge of edges) {
    const from = edge.predecessorNodeCode.trim().toUpperCase()
    const to = edge.successorNodeCode.trim().toUpperCase()
    if (!codes.has(from) || !codes.has(to)) { issues.push(issue('MISSING_EDGE_NODE', 'ROUTE_EDGE', edge.id ?? from, undefined, '依赖引用了不存在的节点')); continue }
    if (from === to) { issues.push(issue('SELF_DEPENDENCY', 'ROUTE_EDGE', edge.id ?? from, undefined, '节点不能依赖自身')); continue }
    if (edge.dependencyType === 'SAME_START' && forPublish) issues.push(issue('UNSUPPORTED_SYNC_RULE', 'ROUTE_EDGE', edge.id ?? `${from}-${to}`, 'dependencyType', 'P0 不支持发布 SAME_START'))
    if (edge.dependencyType === 'QUANTITY' && Number(Boolean(edge.thresholdQty)) + Number(Boolean(edge.thresholdRatio)) !== 1) issues.push(issue('INVALID_QUANTITY_THRESHOLD', 'ROUTE_EDGE', edge.id ?? `${from}-${to}`, 'thresholdQty', '数量依赖必须设置且只能设置数量或比例门槛'))
    outgoing.get(from)!.add(to)
    indegree.set(to, (indegree.get(to) ?? 0) + 1)
  }
  const queue = [...indegree].filter(([, value]) => value === 0).map(([key]) => key)
  let visited = 0
  while (queue.length) {
    const current = queue.shift()!
    visited += 1
    outgoing.get(current)?.forEach(next => { const value = (indegree.get(next) ?? 0) - 1; indegree.set(next, value); if (value === 0) queue.push(next) })
  }
  if (visited !== codes.size) issues.push(issue('ROUTE_CYCLE', 'ROUTE_VERSION', 'draft', 'edges', '路线依赖形成循环'))
  return issues
}

export function routeGraphInput(nodes: ApsRouteNode[], edges: ApsRouteEdge[]) {
  const codes = new Map(nodes.map(node => [node.id, node.nodeCode]))
  return {
    nodes: nodes.map(({ id, operationSpecId, nodeCode, nodeName, displayOrder, quantityMultiplier, terminal }) => ({ id, operationSpecId, nodeCode, nodeName, displayOrder, quantityMultiplier, terminal })),
    edges: edges.map(edge => ({ id: edge.id, predecessorNodeCode: codes.get(edge.predecessorNodeId) ?? '', successorNodeCode: codes.get(edge.successorNodeId) ?? '', dependencyType: edge.dependencyType, thresholdQty: edge.thresholdQty, thresholdRatio: edge.thresholdRatio, transferBatchQty: edge.transferBatchQty, lagSeconds: edge.lagSeconds, consumesOutput: edge.consumesOutput }))
  }
}

function issue(code: string, objectType: string, objectId: string, field: string | undefined, message: string): ApsValidationIssue {
  return { code, objectType, objectId, field, message }
}
