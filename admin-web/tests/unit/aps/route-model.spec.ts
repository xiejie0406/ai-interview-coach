import { describe, expect, it } from 'vitest'
import { validateRouteGraph } from '@/views/aps/routes/route-model'

const node = (id: string) => ({ id, nodeCode: id })

describe('APS route graph', () => {
  it('accepts an explicit branch and merge DAG', () => {
    const nodes = ['A', 'B', 'C', 'D'].map(node)
    const edges = [['A', 'B'], ['A', 'C'], ['B', 'D'], ['C', 'D']].map(([from, to]) => ({ predecessorNodeCode: from, successorNodeCode: to, dependencyType: 'FINISH' as const, lagSeconds: 0, consumesOutput: false }))
    expect(validateRouteGraph(nodes, edges, true)).toEqual([])
  })

  it('finds a cycle and blocks SAME_START only at publish', () => {
    const nodes = ['A', 'B'].map(node)
    const cycle = [{ predecessorNodeCode: 'A', successorNodeCode: 'B', dependencyType: 'FINISH' as const, lagSeconds: 0, consumesOutput: false }, { predecessorNodeCode: 'B', successorNodeCode: 'A', dependencyType: 'SAME_START' as const, lagSeconds: 0, consumesOutput: false }]
    expect(validateRouteGraph(nodes, cycle, false).map(item => item.code)).toEqual(['ROUTE_CYCLE'])
    expect(validateRouteGraph(nodes, cycle, true).map(item => item.code)).toContain('UNSUPPORTED_SYNC_RULE')
  })
})
