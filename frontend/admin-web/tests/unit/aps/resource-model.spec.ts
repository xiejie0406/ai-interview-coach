import { describe, expect, it } from 'vitest'
import { assertUtcHalfOpenWindow, groupSkills, uniqueResources } from '@/views/aps/resources/resource-model'
import type { ApsResource, ApsResourceSkill } from '@/types/aps/resource'

const resource: ApsResource = {
  id: 'r1', workshopId: 'w1', code: 'P-1', name: '人员一', type: 'PERSON',
  capacityValue: 1, capacityUomCode: 'COUNT', status: 'ACTIVE', rowVersion: 0
}

describe('APS resource view model', () => {
  it('keeps one resource row when the same employee has multiple skills', () => {
    const skills = [
      { id: 's1', resourceId: 'r1', code: 'WELD', name: '焊接', level: 3, status: 'ACTIVE', rowVersion: 0 },
      { id: 's2', resourceId: 'r1', code: 'QC', name: '质检', level: 4, status: 'ACTIVE', rowVersion: 0 }
    ] satisfies ApsResourceSkill[]
    expect(uniqueResources([resource, { ...resource }])).toHaveLength(1)
    expect(groupSkills(skills).get('r1')).toHaveLength(2)
  })

  it('rejects local, zero-length and reverse windows before submission', () => {
    expect(() => assertUtcHalfOpenWindow('2026-09-14T08:00:00+08:00', '2026-09-14T09:00:00+08:00')).toThrow(/UTC/)
    expect(() => assertUtcHalfOpenWindow('2026-09-14T00:00:00Z', '2026-09-14T00:00:00Z')).toThrow(/晚于/)
    expect(() => assertUtcHalfOpenWindow('2026-09-14T02:00:00Z', '2026-09-14T01:00:00Z')).toThrow(/晚于/)
    expect(() => assertUtcHalfOpenWindow('2026-09-14T00:00:00Z', '2026-09-14T01:00:00Z')).not.toThrow()
  })
})
