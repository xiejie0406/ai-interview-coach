export type ApsExecutionRunStatus = 'READY' | 'RUNNING' | 'PAUSED' | 'WAIT_QUALITY' | 'COMPLETED' | 'CANCELLED'
export type ApsExecutionAction = 'START' | 'PAUSE' | 'RESUME' | 'CANCEL'
export type ApsOccupancyActivity = 'SETUP' | 'RUN' | 'UNLOAD' | 'WAIT_HOLD' | 'PAUSE_HOLD' | 'TRANSPORT'
export type ApsOccupancyStatus = 'ACTIVE' | 'COMPLETED' | 'CORRECTION' | 'VOID'
export type ApsReportType = 'PROGRESS' | 'COMPLETE' | 'CORRECTION'
export type ApsQualityStatus = 'PENDING' | 'RELEASED' | 'HOLD' | 'REJECTED' | 'CLOSED'
export type ApsQualityDecision = 'HOLD' | 'RELEASE' | 'REJECT' | 'REWORK' | 'SCRAP' | 'USE_AS_IS'
export type ApsDispositionType = 'NONE' | 'REWORK' | 'SCRAP' | 'USE_AS_IS'
export type ApsQuantityOperation = 'RESERVE' | 'UNRESERVE' | 'CONSUME' | 'TRANSFER'

export interface ApsExecutionRun {
  readonly id: string
  readonly planVersionId: string
  readonly planJobId: string
  readonly runNo: number
  readonly status: ApsExecutionRunStatus
  readonly assignedQty: number
  readonly uomCode: string
  readonly actualStartAt: string | null
  readonly actualEndAt: string | null
  readonly pauseReason: string | null
  readonly requestId: string
  readonly rowVersion: number
}

export interface ApsActualOccupancy {
  readonly id: string
  readonly planJobId: string
  readonly executionRunId: string
  readonly planSegmentId: string | null
  readonly sourcePlanAllocationId: string | null
  readonly resourceId: string
  readonly activityType: ApsOccupancyActivity
  readonly startAt: string
  readonly endAt: string | null
  readonly status: ApsOccupancyStatus
  readonly correctionOfId: string | null
  readonly reason: string | null
  readonly rowVersion: number
}

export interface ApsReportQuantities {
  readonly processedQty: number
  readonly goodQty: number
  readonly pendingQty: number
  readonly rejectedQty: number
  readonly scrapQty: number
  readonly transferredQty: number
}

export interface ApsProductionReport {
  readonly id: string
  readonly planJobId: string
  readonly executionRunId: string
  readonly planJobMemberId: string
  readonly taskId: string
  readonly reportType: ApsReportType
  readonly reportedAt: string
  readonly quantities: ApsReportQuantities
  readonly uomCode: string
  readonly defectReason: string | null
  readonly correctionOfId: string | null
  readonly requestId: string
  readonly operatorUserId: string | null
  readonly rowVersion: number
}

export interface ApsOutputLot {
  readonly id: string
  readonly productionLotId: string
  readonly sourceTaskId: string
  readonly sourceReportId: string
  readonly itemId: string
  readonly sourceOutputLotId: string | null
  readonly outputLotNo: string
  readonly qualityStatus: ApsQualityStatus
  readonly totalQty: number
  readonly availableQty: number
  readonly reservedQty: number
  readonly consumedQty: number
  readonly scrappedQty: number
  readonly uomCode: string
  readonly dispositionType: ApsDispositionType
  readonly dispositionReason: string | null
  readonly approvedBy: string | null
  readonly approvedAt: string | null
  readonly rowVersion: number
}

export interface ApsExecutionRunDetail {
  readonly schemaVersion: '1.0'
  readonly contractType: 'EXECUTION_RUN_DETAIL'
  readonly run: ApsExecutionRun
  readonly occupancies: readonly ApsActualOccupancy[]
  readonly reports: readonly ApsProductionReport[]
  readonly outputLots: readonly ApsOutputLot[]
  readonly executionRevision: number
}

export interface ApsCreateExecutionRunInput {
  readonly planVersionId: string
  readonly planJobId: string
  readonly assignedQty: number
  readonly uomCode: string
}

export interface ApsTransitionExecutionInput {
  readonly action: ApsExecutionAction
  readonly expectedRowVersion: number
  readonly occurredAt: string
  readonly reason?: string
}

export interface ApsChangeExecutionResourceInput {
  readonly expectedRowVersion: number
  readonly replacedResourceId: string
  readonly resourceId: string
  readonly planSegmentId?: string
  readonly activityType: ApsOccupancyActivity
  readonly occurredAt: string
  readonly reason?: string
}

export interface ApsAdvanceExecutionPhaseInput {
  readonly expectedRowVersion: number
  readonly occurredAt: string
  readonly reason?: string
}

export interface ApsCreateProductionReportInput {
  readonly expectedRowVersion: number
  readonly planJobMemberId: string
  readonly taskId: string
  readonly reportType: Exclude<ApsReportType, 'CORRECTION'>
  readonly reportedAt: string
  readonly quantities: ApsReportQuantities
  readonly uomCode: string
  readonly defectReason?: string
  readonly operatorUserId?: string
}

export interface ApsCorrectProductionReportInput {
  readonly expectedRunRowVersion: number
  readonly expectedReportRowVersion: number
  readonly reportedAt: string
  readonly quantities: ApsReportQuantities
  readonly reason: string
}

export interface ApsQualityDecisionInput {
  readonly expectedRunRowVersion: number
  readonly expectedOutputLotRowVersion: number
  readonly decision: ApsQualityDecision
  readonly quantity: number
  readonly occurredAt: string
  readonly reason?: string
  readonly createReplenishment: boolean
}

export interface ApsQuantityMovementInput {
  readonly expectedRunRowVersion: number
  readonly expectedOutputLotRowVersion: number
  readonly materialDemandId: string
  readonly targetTaskId: string
  readonly targetExecutionRunId?: string
  readonly operation: ApsQuantityOperation
  readonly quantity: number
  readonly occurredAt: string
  readonly reason?: string
}
