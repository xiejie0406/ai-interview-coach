import type { ApsResource, ApsResourceSkill } from '@/types/aps/resource'

/** 技能是资源的子行，汇总时必须按资源 ID 去重，不能把技能行当成人数。 */
export function uniqueResources(resources: readonly ApsResource[]): ApsResource[] {
  return [...new Map(resources.map(resource => [resource.id, resource])).values()]
}

export function groupSkills(skills: readonly ApsResourceSkill[]): Map<string, ApsResourceSkill[]> {
  const result = new Map<string, ApsResourceSkill[]>()
  for (const skill of skills) {
    const current = result.get(skill.resourceId) ?? []
    current.push(skill)
    result.set(skill.resourceId, current)
  }
  return result
}

export function assertUtcHalfOpenWindow(startAt: string, endAt: string): void {
  if (!startAt.endsWith('Z') || !endAt.endsWith('Z')) {
    throw new Error('时间必须使用 UTC Z 格式')
  }
  if (!Number.isFinite(Date.parse(startAt)) || !Number.isFinite(Date.parse(endAt)) || Date.parse(endAt) <= Date.parse(startAt)) {
    throw new Error('结束时间必须晚于开始时间')
  }
}
