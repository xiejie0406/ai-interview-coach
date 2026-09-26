import { describe, expect, it } from 'vitest'
import { addExplicitDependency, normalizeOrderLines } from '@/views/aps/orders/order-model'

describe('APS multi-product order', () => {
  it('does not infer cross-product dependencies from array order', () => {
    const lines = normalizeOrderLines([
      { lineNo: 20, itemId: 'B', routeVersionId: 'RB', demandQty: 2, uomCode: 'pcs', dependencies: [] },
      { lineNo: 10, itemId: 'A', routeVersionId: 'RA', demandQty: 1, uomCode: 'pcs', dependencies: [] }
    ])
    expect(lines.map(line => line.lineNo)).toEqual([20, 10])
    expect(lines.every(line => (line.dependencies as unknown[]).length === 0)).toBe(true)
  })

  it('keeps only an explicitly added quantity dependency', () => {
    const target: any = { dependencies: [] }
    addExplicitDependency(target, { predecessorLineNo: 1, predecessorNodeCode: 'A-END', successorNodeCode: 'B-START', dependencyType: 'QUANTITY', thresholdQty: 5, transferBatchQty: 2, lagSeconds: 0, consumesOutput: false })
    expect(target.dependencies).toHaveLength(1)
    expect(target.dependencies[0].dependencyType).toBe('QUANTITY')
  })
})
