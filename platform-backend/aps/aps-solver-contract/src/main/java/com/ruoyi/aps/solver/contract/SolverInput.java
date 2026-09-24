package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import java.util.List;

/** 与 solver-input-v1.schema.json 对齐的不可变求解输入。十进制值保留为定点文本。 */
public record SolverInput(String schemaVersion, String contractType, String requestId, String planVersionId,
        Instant capturedAt, long definitionRevision, long executionRevision, String inputHash,
        String hashAlgorithm, String canonicalization, String modelVersion, Scope scope, Horizon horizon,
        BaseVersion baseVersion, Parameters parameters, List<Resource> resources,
        List<AvailabilityWindow> availabilityWindows, List<Task> tasks, List<Dependency> dependencies,
        List<MaterialSupply> materialSupplies, List<MaterialDemand> materialDemands,
        List<SharedBatchCandidate> sharedBatchCandidates, List<PlanLock> locks,
        List<ActualOccupancy> actualOccupancies)
{
    public SolverInput
    {
        resources = copy(resources); availabilityWindows = copy(availabilityWindows); tasks = copy(tasks);
        dependencies = copy(dependencies); materialSupplies = copy(materialSupplies); materialDemands = copy(materialDemands);
        sharedBatchCandidates = copy(sharedBatchCandidates); locks = copy(locks); actualOccupancies = copy(actualOccupancies);
    }

    private static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }

    public enum ResourceType { PERSON, MACHINE, WORKSTATION, TOOL }
    public enum PhaseType { SETUP, RUN, UNLOAD, WAIT, TRANSPORT }
    public enum SegmentResourcePolicy { SAME_RESOURCES, RESELECT_ALLOWED }
    public enum PlanningClass { MANDATORY_DETAIL, FUTURE_CARRY_FORWARD }
    public enum RelationType { FINISH_TO_START, QUANTITY, SAME_START }
    public enum LagBasis { ELAPSED, WORKING }
    public enum DemandType { COMPONENT, TRANSFER, EXTERNAL }
    public enum MaterialStatus { UNAVAILABLE, PARTIAL, AVAILABLE, CONSUMED, CANCELLED }
    public enum ReleaseConfidence { TRUSTED, UNKNOWN, NOT_APPLICABLE }

    public record Scope(String siteCode, List<String> workshopIds)
    {
        public Scope { workshopIds = copy(workshopIds); }
    }
    public record Horizon(Instant startAt, Instant detailEndAt, Instant endAt, Instant planningAnchorAt,
            int timeUnitSeconds, String displayTimeZone) { }
    public record BaseVersion(String planVersionId, String inputHash, Instant freezeEndAt,
            List<BaselineJob> jobs)
    {
        public BaseVersion { jobs = copy(jobs); }

        public BaseVersion(String planVersionId, String inputHash)
        {
            this(planVersionId, inputHash, null, List.of());
        }
    }
    public record BaselineJob(String baselineJobId, List<String> memberTaskIds, Instant startAt,
            Instant endAt, List<String> resourceIds)
    {
        public BaselineJob
        {
            memberTaskIds = copy(memberTaskIds);
            resourceIds = copy(resourceIds);
        }
    }
    public record Parameters(String direction, int maxSolveSeconds, int randomSeed, int solverSearchThreads,
            double absoluteGapLimit, double relativeGapLimit) { }
    public record Resource(String resourceId, ResourceType resourceType, String workCenterId, boolean exclusive,
            String capacity, String capacityUomCode, List<ResourceSkill> skills)
    {
        public Resource { skills = copy(skills); }
    }
    public record ResourceSkill(String skillId, String skillCode, int skillLevel, Instant validFrom,
            Instant validTo, String status) { }
    public record AvailabilityWindow(String availabilityId, String resourceId, Instant startAt, Instant endAt,
            String capacity) { }
    public record Task(String taskId, String orderLineId, String operationSpecId, String workCenterId,
            PlanningClass planningClass, String quantity, String uomCode, Instant earliestStartAt,
            Instant promisedAt, List<Phase> phases)
    {
        public Task { phases = copy(phases); }
    }
    public record Phase(String phaseId, PhaseType phaseType, int sequenceNo, int fixedSeconds,
            String secondsPerUnit, boolean interruptible, int maxSegments, int minSegmentSeconds,
            int resumeSetupSeconds, SegmentResourcePolicy segmentResourcePolicy,
            List<ResourceRequirement> requirements)
    {
        public Phase { requirements = copy(requirements); }

        public Phase(String phaseId, PhaseType phaseType, int sequenceNo, int fixedSeconds,
                String secondsPerUnit, boolean interruptible, int maxSegments, int resumeSetupSeconds,
                List<ResourceRequirement> requirements)
        {
            this(phaseId, phaseType, sequenceNo, fixedSeconds, secondsPerUnit, interruptible, maxSegments,
                    0, resumeSetupSeconds, SegmentResourcePolicy.SAME_RESOURCES, requirements);
        }
    }
    public record ResourceRequirement(String requirementId, ResourceType resourceType, int seatCount,
            String capacityDemand, String requiredSkillCode, Integer minimumSkillLevel,
            boolean holdOnPause, List<String> candidateResourceIds)
    {
        public ResourceRequirement { candidateResourceIds = copy(candidateResourceIds); }

        public ResourceRequirement(String requirementId, ResourceType resourceType, int seatCount,
                String capacityDemand, String requiredSkillCode, Integer minimumSkillLevel,
                List<String> candidateResourceIds)
        {
            this(requirementId, resourceType, seatCount, capacityDemand, requiredSkillCode,
                    minimumSkillLevel, false, candidateResourceIds);
        }
    }
    public record Dependency(String dependencyId, String predecessorTaskId, String successorTaskId,
            RelationType relationType, int lagSeconds, LagBasis lagBasis, String thresholdQty,
            String thresholdRatio, String transferBatchQty, String uomCode, boolean consumesOutput) { }
    public record MaterialSupply(String supplyId, String itemId, String supplyType, String sourceTaskId,
            Instant availableAt, String quantity, String uomCode, String qualityState) { }
    public record MaterialDemand(String demandId, String targetTaskId, String itemId, String sourceTaskId,
            DemandType demandType, String requiredQuantity, String uomCode, String transferBatchQuantity,
            MaterialStatus materialStatus, Instant readyAt) { }
    public record SharedBatchCandidate(String candidateId, String sourceType, String operationSpecId,
            String workCenterId, String compatibilityKey, String capacity, String capacityUomCode,
            int cycleDurationSeconds, List<SharedBatchMember> members)
    {
        public SharedBatchCandidate { members = copy(members); }
    }
    public record SharedBatchMember(String taskId, String quantity, String uomCode) { }
    public record PlanLock(String lockId, String targetType, String targetId, String lockType,
            Instant lockedStartAt, Instant lockedEndAt, List<String> lockedResourceIds, String reason)
    {
        public PlanLock { lockedResourceIds = copy(lockedResourceIds); }
    }
    public record ActualOccupancy(String occupancyId, String planJobId, String executionRunId,
            String sourcePlanSegmentId, String resourceId, ResourceType occupiedResourceRole, String taskId,
            String activityType, Instant startAt, Instant endAt, CurrentPhase currentPhase,
            Integer remainingDurationSeconds, String remainingQuantity, String remainingQuantityUomCode,
            Instant releaseAt, ReleaseConfidence releaseConfidence, List<ActualOccupancyMember> members,
            String sourceRequirementId, Integer sourceSeatNo, String capacityUsed)
    {
        public ActualOccupancy { members = copy(members); }

        /** 兼容 IMP-07 契约夹具；单任务开放占用会自动形成一个成员。 */
        public ActualOccupancy(String occupancyId, String planJobId, String executionRunId,
                String sourcePlanSegmentId, String resourceId, ResourceType occupiedResourceRole, String taskId,
                String activityType, Instant startAt, Instant endAt, CurrentPhase currentPhase,
                Integer remainingDurationSeconds, String remainingQuantity, String remainingQuantityUomCode,
                Instant releaseAt, ReleaseConfidence releaseConfidence)
        {
            this(occupancyId, planJobId, executionRunId, sourcePlanSegmentId, resourceId, occupiedResourceRole,
                    taskId, activityType, startAt, endAt, currentPhase, remainingDurationSeconds,
                    remainingQuantity, remainingQuantityUomCode, releaseAt, releaseConfidence,
                    taskId == null || remainingQuantity == null || remainingQuantityUomCode == null
                            ? List.of() : List.of(new ActualOccupancyMember(taskId, remainingQuantity,
                                    remainingQuantityUomCode)), null, null, null);
        }
    }
    public record ActualOccupancyMember(String taskId, String remainingQuantity, String uomCode) { }
    public record CurrentPhase(String operationSpecId, String phaseId, PhaseType phaseType) { }
}
