import type { ApsLineDependency, ApsOrderLine } from '@/types/aps/manufacturing'

/** 只复制用户显式填写的依赖，不按产品行号或数组顺序推导关系。 */
export function normalizeOrderLines(lines: Array<Partial<ApsOrderLine>>): Array<Record<string, unknown>> {
  const lineNumbers = new Set<number>()
  return lines.map((line, index) => {
    const lineNo = Number(line.lineNo ?? index + 1)
    if (lineNumbers.has(lineNo)) throw new Error(`产品行号重复：${lineNo}`)
    lineNumbers.add(lineNo)
    if (!line.itemId || !line.routeVersionId || Number(line.demandQty) <= 0 || !line.uomCode) throw new Error(`第 ${lineNo} 行缺少产品、路线、数量或单位`)
    return {
      lineNo,
      itemId: line.itemId,
      routeVersionId: line.routeVersionId,
      demandQty: Number(line.demandQty),
      uomCode: line.uomCode.toUpperCase(),
      promisedAt: line.promisedAt || undefined,
      earliestStartAt: line.earliestStartAt || undefined,
      components: [...(line.components ?? [])],
      dependencies: [...(line.dependencies ?? [])]
    }
  })
}

export function addExplicitDependency(target: Partial<ApsOrderLine>, dependency: ApsLineDependency) {
  target.dependencies = [...(target.dependencies ?? []), { ...dependency }]
}

export function expansionCounts(value: { lots: unknown[]; tasks: unknown[]; dependencies: unknown[]; materialDemands: unknown[] }) {
  return { lots: value.lots.length, tasks: value.tasks.length, dependencies: value.dependencies.length, materialDemands: value.materialDemands.length }
}
