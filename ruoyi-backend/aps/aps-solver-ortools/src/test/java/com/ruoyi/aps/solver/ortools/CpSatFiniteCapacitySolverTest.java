package com.ruoyi.aps.solver.ortools;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpSatFiniteCapacitySolverTest
{
    private final CpSatFiniteCapacitySolver solver = new CpSatFiniteCapacitySolver();

    @Test
    void generatesFiniteCapacityCandidateAndHonorsFinishDependency()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task first = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput.Task second = task(11, List.of(phase(21, 3600,
                requirement(31, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput.Dependency dependency = new SolverInput.Dependency(id(40), first.taskId(), second.taskId(),
                SolverInput.RelationType.FINISH_TO_START, 0, SolverInput.LagBasis.ELAPSED,
                null, null, null, null, false);
        SolverInput input = input(List.of(person), List.of(first, second), List.of(dependency));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        assertNotNull(result.candidate());
        PlanCandidate.Job firstJob = jobFor(result.candidate(), first.taskId());
        PlanCandidate.Job secondJob = jobFor(result.candidate(), second.taskId());
        assertFalse(firstJob.endAt().isAfter(secondJob.startAt()));
        assertFalse(overlaps(firstJob, secondJob));
        assertNotNull(result.candidateHash());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void joinsParallelBranchesAtLatestReleasePlusTransportLag()
    {
        SolverInput.Resource firstMachine = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource secondMachine = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource mergeMachine = resource(3, SolverInput.ResourceType.MACHINE);
        SolverInput.Task first = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.MACHINE, firstMachine.resourceId()))), at(9));
        SolverInput.Task second = task(11, List.of(phase(21, 3600,
                requirement(31, SolverInput.ResourceType.MACHINE, secondMachine.resourceId()))), at(10));
        SolverInput.Task merge = task(12, List.of(phase(22, 1800,
                requirement(32, SolverInput.ResourceType.MACHINE, mergeMachine.resourceId()))), at(0));
        List<SolverInput.Dependency> dependencies = List.of(
                new SolverInput.Dependency(id(40), first.taskId(), merge.taskId(),
                        SolverInput.RelationType.FINISH_TO_START, 1800, SolverInput.LagBasis.ELAPSED,
                        null, null, null, null, false),
                new SolverInput.Dependency(id(41), second.taskId(), merge.taskId(),
                        SolverInput.RelationType.FINISH_TO_START, 1800, SolverInput.LagBasis.ELAPSED,
                        null, null, null, null, false));

        SolverResult result = solver.solve(input(List.of(firstMachine, secondMachine, mergeMachine),
                List.of(first, second, merge), dependencies), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        assertEquals(atMinutes(11 * 60 + 30), jobFor(result.candidate(), merge.taskId()).startAt());
    }

    @Test
    void releasesPersonDuringAutomaticMachineRunButKeepsMachineOccupied()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Resource machine = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.Phase setup = phase(20, 1800,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()));
        SolverInput.Phase run = new SolverInput.Phase(id(21), SolverInput.PhaseType.RUN, 2, 7200, "0", false, 1, 0,
                List.of(requirement(31, SolverInput.ResourceType.MACHINE, machine.resourceId())));
        SolverInput.Phase unload = new SolverInput.Phase(id(22), SolverInput.PhaseType.UNLOAD, 3, 1800, "0", false, 1, 0,
                List.of(requirement(32, SolverInput.ResourceType.PERSON, person.resourceId())));
        SolverInput.Task furnace = task(10, List.of(setup, run, unload), at(0));
        SolverInput.Task parallelLabor = task(11, List.of(phase(23, 3600,
                requirement(33, SolverInput.ResourceType.PERSON, person.resourceId()))), atMinutes(30));
        SolverInput input = input(List.of(person, machine), List.of(furnace, parallelLabor), List.of());

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        PlanCandidate.Segment machineRun = result.candidate().segments().stream()
                .filter(value -> value.phaseId().equals(run.phaseId())).findFirst().orElseThrow();
        PlanCandidate.Job labor = jobFor(result.candidate(), parallelLabor.taskId());
        assertFalse(labor.startAt().isBefore(machineRun.startAt()));
        assertFalse(labor.endAt().isAfter(machineRun.endAt()));
    }

    @Test
    void splitsApprovedInterruptibleRunAcrossWindowsAndAddsResumeSetup()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.ResourceRequirement labor = new SolverInput.ResourceRequirement(id(30),
                SolverInput.ResourceType.PERSON, 1, "1", null, null, false, List.of(person.resourceId()));
        SolverInput.Phase run = new SolverInput.Phase(id(20), SolverInput.PhaseType.RUN, 1,
                3 * 3600, "0", true, 2, 3600, 1800,
                SolverInput.SegmentResourcePolicy.SAME_RESOURCES, List.of(labor));
        SolverInput.Task task = task(10, List.of(run), at(0));
        SolverInput source = input(List.of(person), List.of(task), List.of());
        List<SolverInput.AvailabilityWindow> splitWindows = List.of(
                new SolverInput.AvailabilityWindow(id(501), person.resourceId(), at(0), at(2), "1"),
                new SolverInput.AvailabilityWindow(id(502), person.resourceId(), at(3), at(5), "1"));
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(),
                source.horizon(), source.baseVersion(), source.parameters(), source.resources(), splitWindows,
                source.tasks(), source.dependencies(), source.materialSupplies(), source.materialDemands(),
                source.sharedBatchCandidates(), source.locks(), source.actualOccupancies());

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        List<PlanCandidate.Segment> segments = result.candidate().segments();
        assertEquals(2, segments.size());
        assertTrue(segments.get(0).endAt().isBefore(segments.get(1).startAt()));
        assertEquals(3 * 3600 + 1800, segments.stream()
                .mapToLong(value -> java.time.Duration.between(value.startAt(), value.endAt()).getSeconds()).sum());
        assertEquals(List.of(person.resourceId()), result.candidate().allocations().stream()
                .map(PlanCandidate.Allocation::resourceId).distinct().toList());
    }

    @Test
    void keepsHeldMachineReservedAcrossInterruptiblePause()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Resource machine = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.ResourceRequirement labor = new SolverInput.ResourceRequirement(id(30),
                SolverInput.ResourceType.PERSON, 1, "1", null, null, false, List.of(person.resourceId()));
        SolverInput.ResourceRequirement heldMachine = new SolverInput.ResourceRequirement(id(31),
                SolverInput.ResourceType.MACHINE, 1, "1", null, null, true, List.of(machine.resourceId()));
        SolverInput.Phase segmentedRun = new SolverInput.Phase(id(20), SolverInput.PhaseType.RUN, 1,
                3 * 3600, "0", true, 2, 3600, 1800,
                SolverInput.SegmentResourcePolicy.SAME_RESOURCES, List.of(labor, heldMachine));
        SolverInput.Task interrupted = task(10, List.of(segmentedRun), at(0));
        SolverInput.Task competitor = task(11, List.of(phase(21, 3600,
                requirement(32, SolverInput.ResourceType.MACHINE, machine.resourceId()))), at(0));
        SolverInput source = input(List.of(person, machine), List.of(interrupted, competitor), List.of());
        List<SolverInput.AvailabilityWindow> windows = List.of(
                new SolverInput.AvailabilityWindow(id(501), person.resourceId(), at(0), at(2), "1"),
                new SolverInput.AvailabilityWindow(id(502), person.resourceId(), at(3), at(5), "1"),
                new SolverInput.AvailabilityWindow(id(503), machine.resourceId(), at(0), at(6), "1"));
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(),
                source.horizon(), source.baseVersion(), source.parameters(), source.resources(), windows, source.tasks(),
                source.dependencies(), source.materialSupplies(), source.materialDemands(), source.sharedBatchCandidates(),
                source.locks(), source.actualOccupancies());

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        PlanCandidate.Job interruptedJob = jobFor(result.candidate(), interrupted.taskId());
        PlanCandidate.Job competingJob = jobFor(result.candidate(), competitor.taskId());
        List<PlanCandidate.Segment> interruptedSegments = result.candidate().segments().stream()
                .filter(value -> value.jobId().equals(interruptedJob.jobId())).toList();
        assertEquals(2, interruptedSegments.size());
        assertFalse(overlaps(interruptedJob, competingJob));
    }

    @Test
    void softPromisedDateDoesNotTurnEarliestFeasiblePlanIntoFalseInfeasibility()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task task = new SolverInput.Task(id(10), id(110), id(210), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "1", "PCS", at(0), atMinutes(15),
                List.of(phase(20, 3600, requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))));

        SolverResult result = solver.solve(input(List.of(person), List.of(task), List.of()), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertTrue(jobFor(result.candidate(), task.taskId()).endAt().isAfter(task.promisedAt()));
    }

    @Test
    void cancellationAndUnsupportedSameStartFailClosedBeforeSolve()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task first = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput.Task second = task(11, List.of(phase(21, 3600,
                requirement(31, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput base = input(List.of(person), List.of(first, second), List.of());
        assertEquals(SolverResult.PlanStatus.CANCELLED, solver.solve(base, at(13), () -> true).planStatus());
        AtomicInteger polls = new AtomicInteger();
        assertEquals(SolverResult.PlanStatus.CANCELLED,
                solver.solve(base, at(13), () -> polls.incrementAndGet() > 1).planStatus());
        assertTrue(polls.get() >= 2);

        SolverInput.Dependency sameStart = new SolverInput.Dependency(id(40), first.taskId(), second.taskId(),
                SolverInput.RelationType.SAME_START, 0, SolverInput.LagBasis.ELAPSED, null, null, null, null, false);
        SolverResult rejected = solver.solve(input(List.of(person), List.of(first, second), List.of(sameStart)), at(13));
        assertEquals(SolverResult.ResultKind.INVALID_INPUT, rejected.resultKind());
        assertTrue(rejected.problems().stream().anyMatch(value ->
                value.reasonCode() == com.ruoyi.aps.solver.contract.Problem.ReasonCode.UNSUPPORTED_SYNC_RULE));
    }

    @Test
    void keepsFrozenBaselineTimeAndResourcesExactly()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task task = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput source = input(List.of(person), List.of(task), List.of());
        SolverInput.BaseVersion base = new SolverInput.BaseVersion(id(95), "1".repeat(64), at(6),
                List.of(new SolverInput.BaselineJob(id(96), List.of(task.taskId()), at(4), at(5),
                        List.of(person.resourceId()))));

        SolverResult result = solver.solve(withBaseVersion(source, base), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        PlanCandidate.Job job = jobFor(result.candidate(), task.taskId());
        assertEquals(at(4), job.startAt());
        assertEquals(at(5), job.endAt());
        assertEquals(List.of(person.resourceId()), result.candidate().allocations().stream()
                .map(PlanCandidate.Allocation::resourceId).distinct().toList());
    }

    @Test
    void prefersStableNonFrozenBaselineBeforeEarlierCompletion()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task task = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput source = input(List.of(person), List.of(task), List.of());
        SolverInput.BaseVersion base = new SolverInput.BaseVersion(id(95), "1".repeat(64), at(0),
                List.of(new SolverInput.BaselineJob(id(96), List.of(task.taskId()), at(4), at(5),
                        List.of(person.resourceId()))));

        SolverResult result = solver.solve(withBaseVersion(source, base), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        PlanCandidate.Job job = jobFor(result.candidate(), task.taskId());
        assertEquals(at(4), job.startAt());
        assertEquals(at(5), job.endAt());
        assertTrue(result.solverSummary().objectives().stream().anyMatch(value ->
                "BASELINE_DISTURBANCE_UNITS".equals(value.name()) && value.value() == 0d));
    }

    @Test
    void releasesContinuousTransferBatchesAndStartsSuccessorOnlyAfterAlignedRatioGate()
    {
        SolverInput.Resource sourceResource = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource targetResource = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.Phase sourceRun = new SolverInput.Phase(id(20), SolverInput.PhaseType.RUN, 1, 0, "60",
                false, 1, 0, List.of(requirement(30, SolverInput.ResourceType.MACHINE, sourceResource.resourceId())));
        SolverInput.Task source = new SolverInput.Task(id(10), id(110), id(210), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", at(0), at(10), List.of(sourceRun));
        SolverInput.Task target = new SolverInput.Task(id(11), id(111), id(211), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "20", "PCS", at(0), at(10),
                List.of(phase(21, 600, requirement(31, SolverInput.ResourceType.MACHINE, targetResource.resourceId()))));
        SolverInput.Dependency quantity = new SolverInput.Dependency(id(40), source.taskId(), target.taskId(),
                SolverInput.RelationType.QUANTITY, 0, SolverInput.LagBasis.ELAPSED,
                null, "0.5", "20", "PCS", false);

        SolverResult result = solver.solve(input(List.of(sourceResource, targetResource),
                List.of(source, target), List.of(quantity)), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertEquals(at(1), jobFor(result.candidate(), target.taskId()).startAt());
        assertEquals(List.of("20", "20", "20", "20", "20"), result.candidate().segments().stream()
                .filter(segment -> segment.jobId().equals(jobFor(result.candidate(), source.taskId()).jobId()))
                .map(PlanCandidate.Segment::releaseQuantity).toList());
        assertEquals(List.of(atMinutes(20), atMinutes(40), atMinutes(60), atMinutes(80), atMinutes(100)),
                result.candidate().segments().stream()
                        .filter(segment -> segment.jobId().equals(jobFor(result.candidate(), source.taskId()).jobId()))
                        .map(PlanCandidate.Segment::releaseAt).toList());
    }

    @Test
    void anchorsAnIndividualQuantityReleaseSegmentWithoutLockingTheWholeJobInterval()
    {
        SolverInput.Resource sourceResource = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource targetResource = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.Phase sourceRun = new SolverInput.Phase(id(20), SolverInput.PhaseType.RUN, 1, 0, "60",
                false, 1, 0, List.of(requirement(30, SolverInput.ResourceType.MACHINE, sourceResource.resourceId())));
        SolverInput.Task source = new SolverInput.Task(id(10), id(110), id(210), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", at(0), at(10), List.of(sourceRun));
        SolverInput.Task target = new SolverInput.Task(id(11), id(111), id(211), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "20", "PCS", at(0), at(10),
                List.of(phase(21, 600, requirement(31, SolverInput.ResourceType.MACHINE, targetResource.resourceId()))));
        SolverInput.Dependency quantity = new SolverInput.Dependency(id(40), source.taskId(), target.taskId(),
                SolverInput.RelationType.QUANTITY, 0, SolverInput.LagBasis.ELAPSED,
                null, "0.5", "20", "PCS", false);
        SolverInput raw = input(List.of(sourceResource, targetResource), List.of(source, target), List.of(quantity));
        String jobId = stable("job", raw.planVersionId(), source.taskId());
        String secondReleaseSegmentId = stable("segment", jobId, sourceRun.phaseId(), "1", "2");
        SolverInput.PlanLock lock = new SolverInput.PlanLock(id(80), "SEGMENT", secondReleaseSegmentId, "TIME",
                atMinutes(140), atMinutes(160), List.of(), "固定第二个转移批加工段");

        SolverResult result = solver.solve(withLocks(raw, List.of(lock)), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        PlanCandidate.Segment locked = result.candidate().segments().stream()
                .filter(value -> value.segmentId().equals(secondReleaseSegmentId)).findFirst().orElseThrow();
        assertEquals(atMinutes(140), locked.startAt());
        assertEquals(atMinutes(160), locked.endAt());
        assertEquals(at(2), jobFor(result.candidate(), source.taskId()).startAt());
        assertEquals(atMinutes(180), jobFor(result.candidate(), target.taskId()).startAt());
    }

    @Test
    void reservesFiniteOutputAcrossConsumersAndNeverStartsFromFutureProduction()
    {
        SolverInput.Resource sourceResource = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource firstResource = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource secondResource = resource(3, SolverInput.ResourceType.MACHINE);
        SolverInput.Task source = quantityTask(10, "100", sourceResource.resourceId());
        SolverInput.Task first = quantityTask(11, "20", firstResource.resourceId());
        SolverInput.Task second = quantityTask(12, "40", secondResource.resourceId());
        SolverInput.Dependency firstDemand = new SolverInput.Dependency(id(40), source.taskId(), first.taskId(),
                SolverInput.RelationType.QUANTITY, 0, SolverInput.LagBasis.ELAPSED,
                "20", null, "20", "PCS", true);
        SolverInput.Dependency secondDemand = new SolverInput.Dependency(id(41), source.taskId(), second.taskId(),
                SolverInput.RelationType.QUANTITY, 0, SolverInput.LagBasis.ELAPSED,
                "20", null, "20", "PCS", true);

        SolverResult result = solver.solve(input(List.of(sourceResource, firstResource, secondResource),
                List.of(source, first, second), List.of(secondDemand, firstDemand)), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertEquals(atMinutes(20), jobFor(result.candidate(), first.taskId()).startAt());
        assertEquals(atMinutes(60), jobFor(result.candidate(), second.taskId()).startAt());
    }

    @Test
    void schedulesA60PlusB40AsOneFixedCycleBatchAndOccupiesMachineOnce()
    {
        SolverInput.Resource furnace = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Phase cycle = phase(20, 3600,
                requirement(30, SolverInput.ResourceType.MACHINE, furnace.resourceId()));
        SolverInput.Task a = new SolverInput.Task(id(10), id(110), id(210), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "60", "PCS", at(0), at(10), List.of(cycle));
        SolverInput.Task b = new SolverInput.Task(id(11), id(111), id(210), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "40", "PCS", at(0), at(10), List.of(cycle));
        SolverInput.SharedBatchCandidate batch = new SolverInput.SharedBatchCandidate(id(70), "MANUAL_FIXED",
                a.operationSpecId(), a.workCenterId(), "RECIPE_A", "100", "PCS", 3600,
                List.of(new SolverInput.SharedBatchMember(a.taskId(), "60", "PCS"),
                        new SolverInput.SharedBatchMember(b.taskId(), "40", "PCS")));
        SolverInput input = withSharedBatches(input(List.of(furnace), List.of(a, b), List.of()), List.of(batch));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertEquals(1, result.candidate().jobs().size());
        PlanCandidate.Job job = result.candidate().jobs().get(0);
        assertEquals("SHARED_BATCH", job.jobType());
        assertEquals("100", job.plannedQuantity());
        assertEquals(List.of(a.taskId(), b.taskId()), job.memberTaskIds());
        assertEquals(1, result.candidate().allocations().size());
        assertEquals(3600, java.time.Duration.between(job.startAt(), job.endAt()).getSeconds());
        assertEquals("100", result.candidate().segments().get(0).releaseQuantity());
    }

    @Test
    void solvesPrdO100ThresholdTransferSharedFurnaceAndFinalLaborEndToEnd()
    {
        Instant start = Instant.parse("2026-09-14T00:00:00Z");
        java.util.function.IntFunction<Instant> time = hour -> start.plusSeconds(hour * 3600L);
        SolverInput.Resource m1 = resource(701, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource m2 = resource(702, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource furnace = resource(703, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource zhang = resource(711, SolverInput.ResourceType.PERSON);
        SolverInput.Resource li = resource(712, SolverInput.ResourceType.PERSON);
        SolverInput.Resource wang = resource(713, SolverInput.ResourceType.PERSON);
        SolverInput.Resource zhao = resource(714, SolverInput.ResourceType.PERSON);
        SolverInput.Task a10 = new SolverInput.Task(id(720), id(820), id(920), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", time.apply(0), time.apply(9),
                List.of(new SolverInput.Phase(id(721), SolverInput.PhaseType.RUN, 1, 0, "144", false, 1, 0,
                        List.of(requirement(731, SolverInput.ResourceType.MACHINE, m1.resourceId()),
                                requirement(732, SolverInput.ResourceType.PERSON, zhang.resourceId())))));
        SolverInput.Task b10 = new SolverInput.Task(id(722), id(822), id(922), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", time.apply(0), time.apply(9),
                List.of(new SolverInput.Phase(id(723), SolverInput.PhaseType.RUN, 1, 0, "72", false, 1, 0,
                        List.of(requirement(733, SolverInput.ResourceType.MACHINE, m2.resourceId()),
                                requirement(734, SolverInput.ResourceType.PERSON, li.resourceId())))));
        List<SolverInput.Phase> heatPhases = List.of(
                new SolverInput.Phase(id(724), SolverInput.PhaseType.SETUP, 1, 1800, "0", false, 1, 0,
                        List.of(requirement(735, SolverInput.ResourceType.MACHINE, furnace.resourceId()),
                                requirement(736, SolverInput.ResourceType.PERSON, wang.resourceId()))),
                new SolverInput.Phase(id(725), SolverInput.PhaseType.RUN, 2, 7200, "0", false, 1, 0,
                        List.of(requirement(737, SolverInput.ResourceType.MACHINE, furnace.resourceId()))),
                new SolverInput.Phase(id(726), SolverInput.PhaseType.UNLOAD, 3, 1800, "0", false, 1, 0,
                        List.of(requirement(738, SolverInput.ResourceType.MACHINE, furnace.resourceId()),
                                requirement(739, SolverInput.ResourceType.PERSON, wang.resourceId()))));
        SolverInput.Task a20 = new SolverInput.Task(id(730), id(830), id(930), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", time.apply(0), time.apply(9), heatPhases);
        SolverInput.Task b20 = new SolverInput.Task(id(741), id(841), a20.operationSpecId(), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", time.apply(0), time.apply(9), heatPhases);
        SolverInput.Task a30 = new SolverInput.Task(id(742), id(842), id(942), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", time.apply(0), time.apply(9),
                List.of(new SolverInput.Phase(id(743), SolverInput.PhaseType.RUN, 1, 3600, "0", false, 1, 0,
                        List.of(requirement(744, SolverInput.ResourceType.PERSON, zhao.resourceId())))));
        SolverInput.Task b30 = new SolverInput.Task(id(745), id(845), id(945), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "100", "PCS", time.apply(0), time.apply(25),
                List.of(new SolverInput.Phase(id(746), SolverInput.PhaseType.RUN, 1, 3600, "0", false, 1, 0,
                        List.of(requirement(747, SolverInput.ResourceType.PERSON, zhao.resourceId())))));
        List<SolverInput.Dependency> dependencies = List.of(
                new SolverInput.Dependency(id(770), a10.taskId(), b10.taskId(), SolverInput.RelationType.QUANTITY,
                        0, SolverInput.LagBasis.ELAPSED, "50", null, null, "PCS", false),
                finish(771, a10, a20), finish(772, b10, b20), finish(773, a20, a30), finish(774, b20, b30));
        SolverInput.SharedBatchCandidate shared = new SolverInput.SharedBatchCandidate(id(775), "MANUAL_FIXED",
                a20.operationSpecId(), a20.workCenterId(), "O100-FURNACE-A100-B100", "200", "PCS", 7200,
                List.of(new SolverInput.SharedBatchMember(a20.taskId(), "100", "PCS"),
                        new SolverInput.SharedBatchMember(b20.taskId(), "100", "PCS")));
        List<SolverInput.Resource> resources = List.of(m1, m2, furnace, zhang, li, wang, zhao);
        List<SolverInput.AvailabilityWindow> windows = List.of(
                new SolverInput.AvailabilityWindow(id(780), m1.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(781), zhang.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(782), m2.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(783), li.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(784), furnace.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(785), furnace.resourceId(), time.apply(5), time.apply(9), "1"),
                new SolverInput.AvailabilityWindow(id(786), wang.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(787), wang.resourceId(), time.apply(5), time.apply(9), "1"),
                new SolverInput.AvailabilityWindow(id(788), zhao.resourceId(), time.apply(0), time.apply(4), "1"),
                new SolverInput.AvailabilityWindow(id(789), zhao.resourceId(), time.apply(5), time.apply(9), "1"),
                new SolverInput.AvailabilityWindow(id(790), zhao.resourceId(), time.apply(24), time.apply(28), "1"),
                new SolverInput.AvailabilityWindow(id(791), zhao.resourceId(), time.apply(29), time.apply(33), "1"));
        SolverInput input = new SolverInput("1.0", "SOLVER_INPUT", id(90), id(91), start, 7, 3,
                "0".repeat(64), "SHA-256", "JCS-RFC8785", "aps-cpsat-v1",
                new SolverInput.Scope("SITE_01", List.of(id(92))),
                new SolverInput.Horizon(time.apply(0), time.apply(36), time.apply(36), time.apply(0), 60,
                        "Asia/Shanghai"), null,
                new SolverInput.Parameters("FORWARD", 5, 1, 1, 0, 0), resources, windows,
                List.of(a10, b10, a20, b20, a30, b30), dependencies, List.of(), List.of(),
                List.of(shared), List.of(), List.of());

        SolverResult result = solver.solve(input, time.apply(36));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        assertEquals(5, result.candidate().jobs().size());
        assertEquals(time.apply(2), jobFor(result.candidate(), b10.taskId()).startAt());
        PlanCandidate.Job furnaceJob = jobFor(result.candidate(), a20.taskId());
        assertEquals(List.of(a20.taskId(), b20.taskId()), furnaceJob.memberTaskIds());
        assertEquals(time.apply(5), furnaceJob.startAt());
        assertEquals(time.apply(8), furnaceJob.endAt());
        assertEquals(time.apply(9), jobFor(result.candidate(), a30.taskId()).endAt());
        assertEquals(time.apply(25), jobFor(result.candidate(), b30.taskId()).endAt());
        assertEquals(3 * 3600, allocatedSeconds(result.candidate(), furnace.resourceId()));
        assertEquals(3600, allocatedSeconds(result.candidate(), wang.resourceId()));
    }

    @Test
    void honorsJobTimeAndResourceLocksWithoutAutomaticUnlock()
    {
        SolverInput.Resource first = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource lockedResource = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.ResourceRequirement choice = new SolverInput.ResourceRequirement(id(30),
                SolverInput.ResourceType.MACHINE, 1, "1", null, null,
                List.of(first.resourceId(), lockedResource.resourceId()));
        SolverInput.Task task = task(10, List.of(phase(20, 3600, choice)), at(0));
        SolverInput source = input(List.of(first, lockedResource), List.of(task), List.of());
        String jobId = java.util.UUID.nameUUIDFromBytes(String.join("\u0000", "job", source.planVersionId(), task.taskId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        SolverInput.PlanLock lock = new SolverInput.PlanLock(id(80), "JOB", jobId, "FULL", at(2), at(3),
                List.of(lockedResource.resourceId()), "人工确认");
        SolverInput locked = withLocks(source, List.of(lock));

        SolverResult result = solver.solve(locked, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertEquals(at(2), result.candidate().jobs().get(0).startAt());
        assertEquals(at(3), result.candidate().jobs().get(0).endAt());
        assertEquals(List.of(lockedResource.resourceId()), result.candidate().allocations().stream()
                .map(PlanCandidate.Allocation::resourceId).distinct().toList());
    }

    @Test
    void honorsSegmentTimeAndAllocationResourceLocksWithoutRaisingTheirGranularity()
    {
        SolverInput.Resource first = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource lockedResource = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.ResourceRequirement choice = new SolverInput.ResourceRequirement(id(30),
                SolverInput.ResourceType.MACHINE, 1, "1", null, null,
                List.of(first.resourceId(), lockedResource.resourceId()));
        SolverInput.Task task = task(10, List.of(phase(20, 3600, choice)), at(0));
        SolverInput source = input(List.of(first, lockedResource), List.of(task), List.of());
        String jobId = stable("job", source.planVersionId(), task.taskId());
        String segmentId = stable("segment", jobId, task.phases().get(0).phaseId(), "1", "1");
        String allocationId = stable("allocation", segmentId, choice.requirementId(), "1",
                lockedResource.resourceId());
        SolverInput.PlanLock segmentTime = new SolverInput.PlanLock(id(80), "SEGMENT", segmentId, "TIME",
                at(2), at(3), List.of(), "固定运行段");
        SolverInput.PlanLock allocationResource = new SolverInput.PlanLock(id(81), "ALLOCATION", allocationId,
                "RESOURCE", null, null, List.of(lockedResource.resourceId()), "固定设备分配");

        SolverResult result = solver.solve(withLocks(source, List.of(segmentTime, allocationResource)), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        assertEquals(at(2), result.candidate().segments().get(0).startAt());
        assertEquals(at(3), result.candidate().segments().get(0).endAt());
        assertEquals(List.of(lockedResource.resourceId()), result.candidate().allocations().stream()
                .map(PlanCandidate.Allocation::resourceId).distinct().toList());
    }

    @Test
    void waitsForFiniteQualityReleasedMaterialAndIgnoresPendingQualitySupply()
    {
        SolverInput.Resource machine = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Task target = task(10, List.of(phase(20, 600,
                requirement(30, SolverInput.ResourceType.MACHINE, machine.resourceId()))), at(0));
        SolverInput base = input(List.of(machine), List.of(target), List.of());
        List<SolverInput.MaterialSupply> supplies = List.of(
                new SolverInput.MaterialSupply(id(70), id(71), "FIXED_AVAILABLE", null, at(1), "30", "PCS", "AVAILABLE"),
                new SolverInput.MaterialSupply(id(72), id(71), "FIXED_AVAILABLE", null, at(2), "30", "PCS", "PENDING_QUALITY"),
                new SolverInput.MaterialSupply(id(73), id(71), "FIXED_AVAILABLE", null, at(3), "40", "PCS", "AVAILABLE"));
        SolverInput.MaterialDemand demand = new SolverInput.MaterialDemand(id(74), target.taskId(), id(71), null,
                SolverInput.DemandType.EXTERNAL, "60", "PCS", null, SolverInput.MaterialStatus.AVAILABLE, null);

        SolverResult result = solver.solve(withMaterials(base, supplies, List.of(demand)), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertEquals(at(3), result.candidate().jobs().get(0).startAt());
    }

    @Test
    void trustedActualOccupancyBlocksTheResourceUntilItsRelease()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task task = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput source = input(List.of(person), List.of(task), List.of());
        SolverInput.ActualOccupancy occupancy = new SolverInput.ActualOccupancy(id(60), null, id(61), null,
                person.resourceId(), SolverInput.ResourceType.PERSON, task.taskId(), "RUN", at(0), at(2),
                new SolverInput.CurrentPhase(task.operationSpecId(), task.phases().get(0).phaseId(),
                        SolverInput.PhaseType.RUN),
                7200, null, null, at(2), SolverInput.ReleaseConfidence.TRUSTED);
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(),
                source.scope(), source.horizon(), source.baseVersion(), source.parameters(), source.resources(),
                source.availabilityWindows(), source.tasks(), source.dependencies(), source.materialSupplies(),
                source.materialDemands(), source.sharedBatchCandidates(), source.locks(), List.of(occupancy));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertFalse(jobFor(result.candidate(), task.taskId()).startAt().isBefore(at(2)));
    }

    @Test
    void trustedActiveRunBecomesOneFixedCarryAndOnlyUnassignedQuantityIsScheduled()
    {
        SolverInput.Resource machine = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Task task = quantityTask(10, "80", machine.resourceId());
        SolverInput source = input(List.of(machine), List.of(task), List.of());
        SolverInput.ResourceRequirement requirement = task.phases().get(0).requirements().get(0);
        SolverInput.ActualOccupancy occupancy = new SolverInput.ActualOccupancy(id(60), id(62), id(61), id(63),
                machine.resourceId(), SolverInput.ResourceType.MACHINE, task.taskId(), "RUN", at(0), null,
                new SolverInput.CurrentPhase(task.operationSpecId(), task.phases().get(0).phaseId(),
                        SolverInput.PhaseType.RUN),
                3600, "40", "PCS", at(1), SolverInput.ReleaseConfidence.TRUSTED,
                List.of(new SolverInput.ActualOccupancyMember(task.taskId(), "40", "PCS")),
                requirement.requirementId(), 1, "1");
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(),
                source.scope(), source.horizon(), source.baseVersion(), source.parameters(), source.resources(),
                source.availabilityWindows(), source.tasks(), source.dependencies(), source.materialSupplies(),
                source.materialDemands(), source.sharedBatchCandidates(), source.locks(), List.of(occupancy));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        List<PlanCandidate.Job> taskJobs = result.candidate().jobs().stream()
                .filter(value -> value.memberTaskIds().contains(task.taskId())).toList();
        assertEquals(2, taskJobs.size());
        PlanCandidate.Job carry = taskJobs.stream().filter(value -> "CARRY".equals(value.jobType())).findFirst().orElseThrow();
        PlanCandidate.Job future = taskJobs.stream().filter(value -> "SPLIT".equals(value.jobType())).findFirst().orElseThrow();
        assertEquals("40", carry.plannedQuantity());
        assertEquals(id(61), carry.carryRunId());
        assertEquals(at(0), carry.startAt());
        assertEquals(at(1), carry.endAt());
        assertEquals("40", future.plannedQuantity());
        assertFalse(future.startAt().isBefore(at(1)));
    }

    @Test
    void schedulesLaterPhasesInsideTheSameCarryAndDelaysItsSuccessor()
    {
        SolverInput.Resource machine = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.Resource successorMachine = resource(2, SolverInput.ResourceType.MACHINE);
        SolverInput.ResourceRequirement runningRequirement = requirement(30,
                SolverInput.ResourceType.MACHINE, machine.resourceId());
        SolverInput.Phase running = new SolverInput.Phase(id(20), SolverInput.PhaseType.RUN, 1,
                0, "60", false, 1, 0, List.of(runningRequirement));
        SolverInput.Phase unload = new SolverInput.Phase(id(21), SolverInput.PhaseType.UNLOAD, 2,
                3600, "0", false, 1, 0,
                List.of(requirement(31, SolverInput.ResourceType.MACHINE, machine.resourceId())));
        SolverInput.Task task = task(10, List.of(running, unload), at(0));
        SolverInput.Task successor = task(11, List.of(phase(22, 3600,
                requirement(32, SolverInput.ResourceType.MACHINE, successorMachine.resourceId()))), at(0));
        SolverInput.Dependency dependency = finish(40, task, successor);
        SolverInput source = input(List.of(machine, successorMachine), List.of(task, successor), List.of(dependency));
        SolverInput.ActualOccupancy occupancy = new SolverInput.ActualOccupancy(id(60), id(62), id(61), id(63),
                machine.resourceId(), SolverInput.ResourceType.MACHINE, task.taskId(), "RUN", at(0), null,
                new SolverInput.CurrentPhase(task.operationSpecId(), running.phaseId(), SolverInput.PhaseType.RUN),
                3600, "1", "PCS", at(1), SolverInput.ReleaseConfidence.TRUSTED,
                List.of(new SolverInput.ActualOccupancyMember(task.taskId(), "1", "PCS")),
                runningRequirement.requirementId(), 1, "1");
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(),
                source.scope(), source.horizon(), source.baseVersion(), source.parameters(), source.resources(),
                source.availabilityWindows(), source.tasks(), source.dependencies(), source.materialSupplies(),
                source.materialDemands(), source.sharedBatchCandidates(), source.locks(), List.of(occupancy));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        PlanCandidate.Job carry = result.candidate().jobs().stream()
                .filter(value -> "CARRY".equals(value.jobType())).findFirst().orElseThrow();
        List<PlanCandidate.Segment> carrySegments = result.candidate().segments().stream()
                .filter(value -> value.jobId().equals(carry.jobId())).toList();
        assertEquals(2, carrySegments.size());
        assertEquals(running.phaseId(), carrySegments.get(0).phaseId());
        assertEquals(at(0), carrySegments.get(0).startAt());
        assertEquals(at(1), carrySegments.get(0).endAt());
        assertEquals(unload.phaseId(), carrySegments.get(1).phaseId());
        assertFalse(carrySegments.get(1).startAt().isBefore(at(1)));
        assertEquals(carry.endAt(), carrySegments.get(1).endAt());
        assertEquals("1", carrySegments.get(1).releaseQuantity());
        assertFalse(jobFor(result.candidate(), successor.taskId()).startAt().isBefore(carry.endAt()));
    }

    @Test
    void splitsInterruptibleLaterPhaseInsideTheSameCarry()
    {
        SolverInput.Resource machine = resource(1, SolverInput.ResourceType.MACHINE);
        SolverInput.ResourceRequirement currentRequirement = requirement(30,
                SolverInput.ResourceType.MACHINE, machine.resourceId());
        SolverInput.Phase current = new SolverInput.Phase(id(20), SolverInput.PhaseType.SETUP, 1,
                3600, "0", false, 1, 0, List.of(currentRequirement));
        SolverInput.ResourceRequirement laterRequirement = new SolverInput.ResourceRequirement(id(31),
                SolverInput.ResourceType.MACHINE, 1, "1", null, null, false, List.of(machine.resourceId()));
        SolverInput.Phase later = new SolverInput.Phase(id(21), SolverInput.PhaseType.RUN, 2,
                3 * 3600, "0", true, 2, 3600, 1800,
                SolverInput.SegmentResourcePolicy.SAME_RESOURCES, List.of(laterRequirement));
        SolverInput.Task task = task(10, List.of(current, later), at(0));
        SolverInput source = input(List.of(machine), List.of(task), List.of());
        SolverInput.ActualOccupancy occupancy = new SolverInput.ActualOccupancy(id(60), id(62), id(61), id(63),
                machine.resourceId(), SolverInput.ResourceType.MACHINE, task.taskId(), "SETUP", at(0), null,
                new SolverInput.CurrentPhase(task.operationSpecId(), current.phaseId(), SolverInput.PhaseType.SETUP),
                3600, "1", "PCS", at(1), SolverInput.ReleaseConfidence.TRUSTED,
                List.of(new SolverInput.ActualOccupancyMember(task.taskId(), "1", "PCS")),
                currentRequirement.requirementId(), 1, "1");
        List<SolverInput.AvailabilityWindow> windows = List.of(
                new SolverInput.AvailabilityWindow(id(501), machine.resourceId(), at(0), at(3), "1"),
                new SolverInput.AvailabilityWindow(id(502), machine.resourceId(), at(4), at(7), "1"));
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(),
                source.scope(), source.horizon(), source.baseVersion(), source.parameters(), source.resources(),
                windows, source.tasks(), source.dependencies(), source.materialSupplies(), source.materialDemands(),
                source.sharedBatchCandidates(), source.locks(), List.of(occupancy));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus(), result.problems().toString());
        PlanCandidate.Job carry = result.candidate().jobs().stream()
                .filter(value -> "CARRY".equals(value.jobType())).findFirst().orElseThrow();
        List<PlanCandidate.Segment> values = result.candidate().segments().stream()
                .filter(value -> value.jobId().equals(carry.jobId())).toList();
        assertEquals(3, values.size());
        assertEquals(3 * 3600 + 1800, values.stream().skip(1)
                .mapToLong(value -> java.time.Duration.between(value.startAt(), value.endAt()).getSeconds()).sum());
        assertTrue(values.get(1).endAt().isBefore(values.get(2).startAt()));
        assertEquals("1", values.get(2).releaseQuantity());
    }

    @Test
    void unknownActualOccupancyFailsClosedWithoutInventingAReleaseTime()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task task = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput source = input(List.of(person), List.of(task), List.of());
        SolverInput.ActualOccupancy occupancy = new SolverInput.ActualOccupancy(id(60), null, id(61), null,
                person.resourceId(), SolverInput.ResourceType.PERSON, task.taskId(), "PAUSE_HOLD", at(0), null,
                null, null, null, null, null, SolverInput.ReleaseConfidence.UNKNOWN);
        SolverInput input = new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(),
                source.planVersionId(), source.capturedAt(), source.definitionRevision(), source.executionRevision(),
                source.inputHash(), source.hashAlgorithm(), source.canonicalization(), source.modelVersion(),
                source.scope(), source.horizon(), source.baseVersion(), source.parameters(), source.resources(),
                source.availabilityWindows(), source.tasks(), source.dependencies(), source.materialSupplies(),
                source.materialDemands(), source.sharedBatchCandidates(), source.locks(), List.of(occupancy));

        SolverResult result = solver.solve(input, at(13));

        assertEquals(SolverResult.PlanStatus.CONFLICT, result.planStatus());
        assertTrue(result.problems().stream().anyMatch(problem ->
                problem.reasonCode() == com.ruoyi.aps.solver.contract.Problem.ReasonCode.OPEN_OCCUPANCY_UNKNOWN_RELEASE));
    }

    @Test
    void prioritizesTotalTardinessBeforeTotalCompletionAndReportsObjectiveLayers()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task looseDue = new SolverInput.Task(id(10), id(110), id(210), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "1", "PCS", at(0), at(10),
                List.of(phase(20, 3600, requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))));
        SolverInput.Task tightDue = new SolverInput.Task(id(11), id(111), id(211), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "1", "PCS", at(0), at(1),
                List.of(phase(21, 3600, requirement(31, SolverInput.ResourceType.PERSON, person.resourceId()))));

        SolverResult result = solver.solve(input(List.of(person), List.of(looseDue, tightDue), List.of()), at(13));

        assertEquals(at(1), jobFor(result.candidate(), tightDue.taskId()).endAt());
        assertEquals(at(2), jobFor(result.candidate(), looseDue.taskId()).endAt());
        assertEquals(List.of("HARD_CONSTRAINT_FEASIBILITY", "TOTAL_TARDINESS_UNITS",
                        "UNPLANNED_MANDATORY_TASKS", "TOTAL_COMPLETION_UNITS"),
                result.solverSummary().objectives().stream().map(SolverResult.Objective::name).toList());
    }

    @Test
    void carriesFutureTasksWithoutCreatingDetailedJobs()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task mandatory = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput.Task future = new SolverInput.Task(id(11), id(111), id(211), id(99),
                SolverInput.PlanningClass.FUTURE_CARRY_FORWARD, "1", "PCS", at(9), at(11),
                List.of(phase(21, 3600, requirement(31, SolverInput.ResourceType.PERSON, person.resourceId()))));

        SolverResult result = solver.solve(input(List.of(person), List.of(mandatory, future), List.of()), at(13));

        assertEquals(SolverResult.PlanStatus.FEASIBLE, result.planStatus());
        assertEquals(List.of(future.taskId()), result.candidate().futureCarryForwardTaskIds());
        assertTrue(result.candidate().jobs().stream().noneMatch(job -> job.memberTaskIds().contains(future.taskId())));
    }

    @Test
    void distinguishesProvenInfeasibleFromTimeoutOrInvalidInput()
    {
        SolverInput.Resource person = resource(1, SolverInput.ResourceType.PERSON);
        SolverInput.Task first = task(10, List.of(phase(20, 3600,
                requirement(30, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput.Task second = task(11, List.of(phase(21, 3600,
                requirement(31, SolverInput.ResourceType.PERSON, person.resourceId()))), at(0));
        SolverInput.Dependency forward = new SolverInput.Dependency(id(40), first.taskId(), second.taskId(),
                SolverInput.RelationType.FINISH_TO_START, 0, SolverInput.LagBasis.ELAPSED,
                null, null, null, null, false);
        SolverInput.Dependency backward = new SolverInput.Dependency(id(41), second.taskId(), first.taskId(),
                SolverInput.RelationType.FINISH_TO_START, 0, SolverInput.LagBasis.ELAPSED,
                null, null, null, null, false);

        SolverResult result = solver.solve(input(List.of(person), List.of(first, second),
                List.of(forward, backward)), at(13));

        assertEquals(SolverResult.PlanStatus.CONFLICT, result.planStatus());
        assertEquals(SolverResult.SolverStatus.INFEASIBLE, result.solverStatus());
        assertEquals(SolverResult.ResultKind.INFEASIBLE_PROVEN, result.resultKind());
    }

    private SolverInput input(List<SolverInput.Resource> resources, List<SolverInput.Task> tasks,
            List<SolverInput.Dependency> dependencies)
    {
        List<SolverInput.AvailabilityWindow> windows = resources.stream().map(resource ->
                new SolverInput.AvailabilityWindow(id(500 + Integer.parseInt(resource.resourceId().substring(32))),
                        resource.resourceId(), at(0), at(12), "1")).toList();
        return new SolverInput("1.0", "SOLVER_INPUT", id(90), id(91), at(0), 7, 3, "0".repeat(64),
                "SHA-256", "JCS-RFC8785", "aps-cpsat-v1", new SolverInput.Scope("SITE_01", List.of(id(92))),
                new SolverInput.Horizon(at(0), at(8), at(12), at(0), 60, "Asia/Shanghai"), null,
                new SolverInput.Parameters("FORWARD", 5, 1, 1, 0, 0), resources, windows, tasks, dependencies,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SolverInput.Resource resource(int no, SolverInput.ResourceType type)
    {
        return new SolverInput.Resource(id(no), type, id(99), true, "1", null, List.of());
    }

    private SolverInput withSharedBatches(SolverInput source, List<SolverInput.SharedBatchCandidate> batches)
    {
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(), source.planVersionId(),
                source.capturedAt(), source.definitionRevision(), source.executionRevision(), source.inputHash(),
                source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(), source.horizon(),
                source.baseVersion(), source.parameters(), source.resources(), source.availabilityWindows(), source.tasks(),
                source.dependencies(), source.materialSupplies(), source.materialDemands(), batches, source.locks(),
                source.actualOccupancies());
    }

    private SolverInput withLocks(SolverInput source, List<SolverInput.PlanLock> locks)
    {
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(), source.planVersionId(),
                source.capturedAt(), source.definitionRevision(), source.executionRevision(), source.inputHash(),
                source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(), source.horizon(),
                source.baseVersion(), source.parameters(), source.resources(), source.availabilityWindows(), source.tasks(),
                source.dependencies(), source.materialSupplies(), source.materialDemands(), source.sharedBatchCandidates(),
                locks, source.actualOccupancies());
    }

    private SolverInput withBaseVersion(SolverInput source, SolverInput.BaseVersion baseVersion)
    {
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(), source.planVersionId(),
                source.capturedAt(), source.definitionRevision(), source.executionRevision(), source.inputHash(),
                source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(), source.horizon(),
                baseVersion, source.parameters(), source.resources(), source.availabilityWindows(), source.tasks(),
                source.dependencies(), source.materialSupplies(), source.materialDemands(), source.sharedBatchCandidates(),
                source.locks(), source.actualOccupancies());
    }

    private SolverInput withMaterials(SolverInput source, List<SolverInput.MaterialSupply> supplies,
            List<SolverInput.MaterialDemand> demands)
    {
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(), source.planVersionId(),
                source.capturedAt(), source.definitionRevision(), source.executionRevision(), source.inputHash(),
                source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(), source.horizon(),
                source.baseVersion(), source.parameters(), source.resources(), source.availabilityWindows(), source.tasks(),
                source.dependencies(), supplies, demands, source.sharedBatchCandidates(), source.locks(),
                source.actualOccupancies());
    }

    private SolverInput.Task task(int no, List<SolverInput.Phase> phases, Instant earliest)
    {
        return new SolverInput.Task(id(no), id(100 + no), id(200 + no), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, "1", "PCS", earliest, at(10), phases);
    }

    private SolverInput.Task quantityTask(int no, String quantity, String resourceId)
    {
        return new SolverInput.Task(id(no), id(100 + no), id(200 + no), id(99),
                SolverInput.PlanningClass.MANDATORY_DETAIL, quantity, "PCS", at(0), at(10),
                List.of(new SolverInput.Phase(id(300 + no), SolverInput.PhaseType.RUN, 1, 0, "60", false, 1, 0,
                        List.of(requirement(400 + no, SolverInput.ResourceType.MACHINE, resourceId)))));
    }

    private SolverInput.Phase phase(int no, int seconds, SolverInput.ResourceRequirement... requirements)
    {
        return new SolverInput.Phase(id(no), SolverInput.PhaseType.RUN, 1, seconds, "0", false, 1, 0,
                List.of(requirements));
    }

    private SolverInput.ResourceRequirement requirement(int no, SolverInput.ResourceType type, String resourceId)
    {
        return new SolverInput.ResourceRequirement(id(no), type, 1, "1", null, null, List.of(resourceId));
    }

    private SolverInput.Dependency finish(int no, SolverInput.Task predecessor, SolverInput.Task successor)
    {
        return new SolverInput.Dependency(id(no), predecessor.taskId(), successor.taskId(),
                SolverInput.RelationType.FINISH_TO_START, 0, SolverInput.LagBasis.ELAPSED,
                null, null, null, null, false);
    }

    private PlanCandidate.Job jobFor(PlanCandidate candidate, String taskId)
    {
        return candidate.jobs().stream().filter(value -> value.memberTaskIds().contains(taskId)).findFirst().orElseThrow();
    }

    private boolean overlaps(PlanCandidate.Job left, PlanCandidate.Job right)
    {
        return left.startAt().isBefore(right.endAt()) && right.startAt().isBefore(left.endAt());
    }

    private long allocatedSeconds(PlanCandidate candidate, String resourceId)
    {
        java.util.Map<String, PlanCandidate.Segment> segments = candidate.segments().stream()
                .collect(java.util.stream.Collectors.toMap(PlanCandidate.Segment::segmentId, value -> value));
        return candidate.allocations().stream().filter(value -> value.resourceId().equals(resourceId))
                .map(PlanCandidate.Allocation::segmentId).map(segments::get)
                .mapToLong(value -> java.time.Duration.between(value.startAt(), value.endAt()).getSeconds()).sum();
    }

    private Instant at(int hour) { return Instant.parse("2026-09-15T00:00:00Z").plusSeconds(hour * 3600L); }
    private Instant atMinutes(int minutes) { return Instant.parse("2026-09-15T00:00:00Z").plusSeconds(minutes * 60L); }
    private String stable(String... values) { return java.util.UUID.nameUUIDFromBytes(String.join("\u0000", values)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); }
    private String id(int no) { return String.format("00000000-0000-4000-8000-%012d", no); }
}
