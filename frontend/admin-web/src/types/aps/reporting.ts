export type ApsReportDecimal = number | string
export type ApsEtaState = 'KNOWN' | 'UNKNOWN'

export interface ApsReportMetadata {
  readonly siteCode: string
  readonly zoneId: string
  readonly fromAt?: string
  readonly toAt?: string
  readonly baselinePlanVersionId?: string
  readonly currentPlanVersionId?: string
  readonly dataCutoffAt: string
  readonly generatedAt: string
}

export interface ApsDailyTaskRow {
  readonly workshopId?: string
  readonly workshopCode?: string
  readonly workshopName?: string
  readonly workCenterId?: string
  readonly workCenterCode?: string
  readonly workCenterName?: string
  readonly operationSpecId: string
  readonly operationCode: string
  readonly operationName: string
  readonly orderId: string
  readonly orderNo: string
  readonly orderLineId: string
  readonly lineNo: number
  readonly itemId: string
  readonly itemCode: string
  readonly itemName: string
  readonly productionLotId: string
  readonly lotNo: string
  readonly taskId: string
  readonly taskCode: string
  readonly taskName: string
  readonly taskStatus: string
  readonly uomCode: string
  readonly promisedAt?: string
  readonly baselineStartAt?: string
  readonly baselineEndAt?: string
  readonly baselinePlannedQty: ApsReportDecimal
  readonly baselinePersonHours: ApsReportDecimal
  readonly baselineMachineHours: ApsReportDecimal
  readonly currentStartAt?: string
  readonly currentEndAt?: string
  readonly currentPlannedQty: ApsReportDecimal
  readonly currentPersonHours: ApsReportDecimal
  readonly currentMachineHours: ApsReportDecimal
  readonly actualProcessedQty: ApsReportDecimal
  readonly actualGoodQty: ApsReportDecimal
  readonly rejectedQty: ApsReportDecimal
  readonly scrapQty: ApsReportDecimal
  readonly transferredQty: ApsReportDecimal
  readonly actualPersonHours: ApsReportDecimal
  readonly actualMachineHours: ApsReportDecimal
  readonly achievementRatio?: ApsReportDecimal
  readonly carryoverQty: ApsReportDecimal
  readonly delayed: boolean
  readonly plannedPersonResourceIds: readonly string[]
  readonly plannedMachineResourceIds: readonly string[]
  readonly actualPersonResourceIds: readonly string[]
  readonly actualMachineResourceIds: readonly string[]
  readonly reasonCodes: readonly string[]
}

export interface ApsDailyProductionReport {
  readonly businessDate: string
  readonly metadata: ApsReportMetadata
  readonly rows: readonly ApsDailyTaskRow[]
}

export interface ApsPersonCapacityRow {
  readonly resourceId: string
  readonly resourceCode: string
  readonly resourceName: string
  readonly workshopId: string
  readonly workshopCode: string
  readonly workshopName: string
  readonly workCenterId?: string
  readonly workCenterCode?: string
  readonly workCenterName?: string
  readonly teamName?: string
  readonly skillCodes: readonly string[]
  readonly availableHours: ApsReportDecimal
  readonly scheduledHours: ApsReportDecimal
  readonly remainingHours: ApsReportDecimal
  readonly overloaded: boolean
}

export interface ApsSkillCapacityRow {
  readonly skillCode: string
  readonly availablePotentialHours: ApsReportDecimal
  readonly scheduledHours: ApsReportDecimal
  readonly unplannedRequiredHours: ApsReportDecimal
  readonly peakRequiredPeople: number
  readonly peakQualifiedPeople: number
  readonly peakShortagePeople: number
  readonly peakAt?: string
  readonly reasonCodes: readonly string[]
}

export interface ApsLaborCapacityReport {
  readonly metadata: ApsReportMetadata
  readonly people: readonly ApsPersonCapacityRow[]
  readonly skills: readonly ApsSkillCapacityRow[]
  readonly aggregationNotice: string
}

export interface ApsDeliveryReason {
  readonly code: string
  readonly objectType: string
  readonly objectId: string
  readonly detail: string
}

export interface ApsOrderLineDeliveryRow {
  readonly orderLineId: string
  readonly lineNo: number
  readonly itemId: string
  readonly itemCode: string
  readonly itemName: string
  readonly uomCode: string
  readonly demandQty: ApsReportDecimal
  readonly releasedTerminalQty: ApsReportDecimal
  readonly lineStatus: string
  readonly promisedAt?: string
  readonly etaState: ApsEtaState
  readonly expectedProductionAt?: string
  readonly knownLowerBoundAt?: string
  readonly reasons: readonly ApsDeliveryReason[]
}

export interface ApsOrderDeliveryRow {
  readonly orderId: string
  readonly orderNo: string
  readonly orderStatus: string
  readonly promisedAt?: string
  readonly etaState: ApsEtaState
  readonly expectedProductionAt?: string
  readonly knownLowerBoundAt?: string
  readonly delaySeconds?: number
  readonly productionCompleted: boolean
  readonly orderClosed: boolean
  readonly reasons: readonly ApsDeliveryReason[]
  readonly lines: readonly ApsOrderLineDeliveryRow[]
}

export interface ApsOrderDeliveryReport {
  readonly metadata: ApsReportMetadata
  readonly orders: readonly ApsOrderDeliveryRow[]
}

export interface ApsDailyReportQuery {
  readonly businessDate: string
  readonly workshopId?: string
  readonly workCenterId?: string
}

export interface ApsLaborReportQuery {
  readonly fromDate: string
  readonly toDate: string
  readonly workshopId?: string
  readonly workCenterId?: string
  readonly skillCode?: string
}

export interface ApsOrderReportQuery {
  readonly orderId?: string
}
