import { describe, expect, it } from 'vitest'

import { buildApsWorkbenchModels, filterApsWorkbenchModels } from '@/views/aps/workbench/workbench-model'
import type { ApsPlanVersionDetail } from '@/types/aps/planning'
import type { ApsResource } from '@/types/aps/resource'

const detail: ApsPlanVersionDetail = {
  schemaVersion: '1.0',
  contractType: 'PLAN_VERSION_DETAIL',
  version: {
    planVersionId: 'plan', baseVersionId: 'base', versionNo: 2, versionName: '候选计划',
    status: 'FEASIBLE', definitionRevision: 1, executionRevision: 1,
    inputHash: 'a'.repeat(64), rowVersion: 3, updatedAt: '2026-09-15T00:00:00Z'
  },
  candidateHash: 'b'.repeat(64),
  solverStatus: 'OPTIMAL',
  resultKind: 'FEASIBLE',
  unplannedTaskIds: [],
  inputCapturedAt: '2026-09-15T00:00:00Z',
  latestFactUpdatedAt: '2026-09-14T23:59:59Z',
  stale: false,
  jobs: [{
    jobId: 'job', operationSpecId: 'operation', workCenterId: 'center', jobCode: 'JOB-1',
    jobType: 'NORMAL', batchCode: null, plannedQty: '10', uomCode: 'PCS', capacityValue: null,
    capacityUomCode: null, compatibilityKey: null, carryRunId: null, startAt: '2026-09-15T01:00:00Z',
    endAt: '2026-09-15T02:00:00Z',
    members: [{ id: 'member', taskId: 'task', memberNo: 1, plannedQty: '10', uomCode: 'PCS' }]
  }],
  segments: [{
    id: 'segment', jobId: 'job', phaseId: 'phase', segmentNo: 1, phaseType: 'RUN',
    startAt: '2026-09-15T01:00:00Z', endAt: '2026-09-15T02:00:00Z', plannedQty: '10',
    releaseAt: null, releaseQty: null, uomCode: 'PCS'
  }],
  allocations: [
    { id: 'machine-allocation', segmentId: 'segment', phaseId: 'phase', requirementId: 'machine-req', resourceId: 'machine', allocationRole: 'MACHINE', seatNo: 1, capacityUsed: '1' },
    { id: 'person-allocation', segmentId: 'segment', phaseId: 'phase', requirementId: 'person-req', resourceId: 'person', allocationRole: 'PERSON', seatNo: 1, capacityUsed: '1' }
  ],
  locks: [{
    id: 'lock', targetType: 'ALLOCATION', jobId: 'job', segmentId: 'segment',
    allocationId: 'person-allocation', lockType: 'TIME', lockedStartAt: '2026-09-15T01:00:00Z',
    lockedEndAt: '2026-09-15T02:00:00Z', lockedResourceId: null, reason: '人员时间已确认', rowVersion: 0
  }],
  problems: []
}

const resources: ApsResource[] = [
  { id: 'machine', workshopId: 'workshop', workCenterId: 'center', code: 'M-01', name: '设备一', type: 'MACHINE', capacityValue: 1, capacityUomCode: 'COUNT', status: 'ACTIVE', rowVersion: 0 },
  { id: 'person', workshopId: 'workshop', workCenterId: 'center', code: 'P-01', name: '人员一', type: 'PERSON', capacityValue: 1, capacityUomCode: 'COUNT', status: 'ACTIVE', rowVersion: 0 }
]

describe('APS 生产工作台领域投影', () => {
  it('形成工序、设备、人员三套稳定视图且保留细粒度锁', () => {
    const result = buildApsWorkbenchModels(detail, resources)

    expect(result.processGantt.tasks).toHaveLength(1)
    expect(result.processGantt.tasks[0].taskId).toBe('job')
    expect(result.equipmentTimeline.resources.map((value) => value.resourceId)).toEqual(['machine'])
    expect(result.equipmentTimeline.segments[0]).toMatchObject({ timelineItemId: 'machine-allocation', segmentId: 'segment', allocationId: 'machine-allocation', editable: true })
    expect(result.personnelTimeline.resources.map((value) => value.resourceId)).toEqual(['person'])
    expect(result.personnelTimeline.segments[0]).toMatchObject({ timelineItemId: 'person-allocation', segmentId: 'segment', allocationId: 'person-allocation', editable: false })
  })

  it('拒绝悬空资源分配而不是生成不完整视图', () => {
    expect(() => buildApsWorkbenchModels(detail, resources.slice(0, 1)))
      .toThrow('不存在的资源 person')
  })

  it('筛选后三种视图仍指向同一稳定作业身份', () => {
    const models = buildApsWorkbenchModels(detail, resources)
    const personnel = filterApsWorkbenchModels(models, { resourceId: 'person', phase: 'RUN' })

    expect(personnel.processGantt.tasks.map((value) => value.taskId)).toEqual(['job'])
    expect(personnel.equipmentTimeline.segments).toEqual([])
    expect(personnel.personnelTimeline.segments).toHaveLength(1)
    expect(filterApsWorkbenchModels(models, { searchText: '不存在的作业' }).processGantt.tasks).toEqual([])
  })
})
