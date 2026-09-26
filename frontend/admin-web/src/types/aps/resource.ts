export type ApsLifecycleStatus = 'ACTIVE' | 'INACTIVE'
export type ApsResourceStatus = ApsLifecycleStatus | 'MAINTENANCE'
export type ApsResourceType = 'PERSON' | 'MACHINE' | 'WORKSTATION' | 'TOOL'
export type ApsAvailabilityType = 'AVAILABLE' | 'UNAVAILABLE' | 'OVERTIME' | 'LEAVE' | 'MAINTENANCE'

export interface ApsWorkshop {
  id: string
  code: string
  name: string
  managerUserId?: string
  status: ApsLifecycleStatus
  remark?: string
  rowVersion: number
}

export interface ApsWorkCenter {
  id: string
  workshopId: string
  code: string
  name: string
  centerType: 'MACHINE' | 'LABOR' | 'MIXED' | 'BATCH'
  concurrentCapacity: number
  capacityUomCode: string
  status: ApsLifecycleStatus
  remark?: string
  rowVersion: number
}

export interface ApsResource {
  id: string
  workshopId: string
  workCenterId?: string
  code: string
  name: string
  type: ApsResourceType
  ruoyiUserId?: string
  teamName?: string
  capacityValue: number
  capacityUomCode: string
  status: ApsResourceStatus
  remark?: string
  rowVersion: number
}

export interface ApsResourceSkill {
  id: string
  resourceId: string
  code: string
  name: string
  level: number
  validFrom?: string
  validTo?: string
  certificateRef?: string
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
  rowVersion: number
}

export interface ApsAvailability {
  id: string
  resourceId: string
  type: ApsAvailabilityType
  startAt: string
  endAt: string
  capacityRatio: number
  sourceType: string
  sourceRef?: string
  reason?: string
  rowVersion: number
}

export interface ApsReadinessIssue {
  code: string
  objectType: string
  objectId: string
  message: string
}

export interface ApsCandidate {
  resourceId: string
  resourceCode: string
  resourceName: string
}

export interface ApsNetWindow {
  start: string
  end: string
  capacityRatio: number
}
