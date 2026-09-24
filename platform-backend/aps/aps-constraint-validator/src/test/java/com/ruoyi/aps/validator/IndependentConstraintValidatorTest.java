package com.ruoyi.aps.validator;

import java.time.Instant;
import java.util.List;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.PlanCandidate.Allocation;
import com.ruoyi.aps.solver.contract.PlanCandidate.Job;
import com.ruoyi.aps.solver.contract.PlanCandidate.Segment;
import com.ruoyi.aps.solver.contract.Problem.ReasonCode;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInput.AvailabilityWindow;
import com.ruoyi.aps.solver.contract.SolverInput.ActualOccupancy;
import com.ruoyi.aps.solver.contract.SolverInput.PlanLock;
import com.ruoyi.aps.solver.contract.SolverInput.ReleaseConfidence;
import com.ruoyi.aps.solver.contract.SolverInput.SharedBatchCandidate;
import com.ruoyi.aps.solver.contract.SolverInput.SharedBatchMember;
import com.ruoyi.aps.solver.contract.SolverInput.Dependency;
import com.ruoyi.aps.solver.contract.SolverInput.Horizon;
import com.ruoyi.aps.solver.contract.SolverInput.LagBasis;
import com.ruoyi.aps.solver.contract.SolverInput.Parameters;
import com.ruoyi.aps.solver.contract.SolverInput.Phase;
import com.ruoyi.aps.solver.contract.SolverInput.PhaseType;
import com.ruoyi.aps.solver.contract.SolverInput.PlanningClass;
import com.ruoyi.aps.solver.contract.SolverInput.RelationType;
import com.ruoyi.aps.solver.contract.SolverInput.Resource;
import com.ruoyi.aps.solver.contract.SolverInput.ResourceRequirement;
import com.ruoyi.aps.solver.contract.SolverInput.ResourceSkill;
import com.ruoyi.aps.solver.contract.SolverInput.ResourceType;
import com.ruoyi.aps.solver.contract.SolverInput.Scope;
import com.ruoyi.aps.solver.contract.SolverInput.Task;
import com.ruoyi.aps.solver.contract.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndependentConstraintValidatorTest
{
    private static final String HASH = "0".repeat(64);
    private final IndependentConstraintValidator validator = new IndependentConstraintValidator();

    @Test
    void validInputAndCandidatePassWithoutCallingSolver()
    {
        Resource person = person(1, true, "1");
        Task task = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        SolverInput input = input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(task), List.of());
        PlanCandidate candidate = candidate(List.of(job(50, task, 2, 3)),
                List.of(segment(60, 50, task.phases().get(0), 1, 2, 3)),
                List.of(allocation(70, 60, task.phases().get(0).requirements().get(0), person, 1, "1")));

        ValidationResult readiness = validator.validateInput(input, null, at(0));
        assertThat(readiness.validationStatus()).as(readiness.problems().toString()).isEqualTo(ValidationResult.Status.PASS);
        ValidationResult result = validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PUBLISH_PRECHECK, at(4));
        assertThat(result.validationStatus()).isEqualTo(ValidationResult.Status.PASS);
        assertThat(result.publishable()).isTrue();
    }

    @Test
    void emptyCandidateCannotClaimMandatoryTaskCoverage()
    {
        Resource person = person(1, true, "1");
        Task task = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        SolverInput input = input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(task), List.of());
        PlanCandidate empty = candidate(List.of(), List.of(), List.of());

        ValidationResult result = validator.validateCandidate(input, empty, HASH, 7, 3,
                ValidationResult.Scope.PLAN_CANDIDATE, at(4));

        assertThat(result.validationStatus()).isEqualTo(ValidationResult.Status.FAIL);
        assertThat(result.problems()).extracting(problem -> problem.reasonCode()).contains(ReasonCode.NO_COMMON_WINDOW);
    }

    @Test
    void nonInterruptibleTaskCannotPretendLunchBreakIsContinuous()
    {
        Resource person = person(1, true, "1");
        Task task = task(10, 20, 30, false, 5 * 3600, 1, List.of(person.resourceId()));
        SolverInput input = input(List.of(person), List.of(
                window(40, person.resourceId(), 1, 5, "1"),
                window(41, person.resourceId(), 6, 10, "1")), List.of(task), List.of());

        assertThat(validator.validateInput(input, null, at(0)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.NO_COMMON_WINDOW);
    }

    @Test
    void interruptibleTaskCanUseTwoAllowedSegmentsAndResumeSetup()
    {
        Resource person = person(1, true, "1");
        Task task = task(10, 20, 30, true, 5 * 3600 + 50 * 60, 1, List.of(person.resourceId()));
        Phase source = task.phases().get(0);
        Phase phase = new Phase(source.phaseId(), source.phaseType(), 1, source.fixedSeconds(), source.secondsPerUnit(),
                true, 2, 30 * 60, 10 * 60, SolverInput.SegmentResourcePolicy.SAME_RESOURCES,
                source.requirements());
        task = new Task(task.taskId(), task.orderLineId(), task.operationSpecId(), task.workCenterId(),
                task.planningClass(), task.quantity(), task.uomCode(), task.earliestStartAt(), task.promisedAt(), List.of(phase));
        SolverInput input = input(List.of(person), List.of(window(40, person.resourceId(), 1, 5, "1"),
                window(41, person.resourceId(), 6, 10, "1")), List.of(task), List.of());
        PlanCandidate candidate = candidate(List.of(job(50, task, 1, 8)), List.of(
                segment(60, 50, phase, 1, 1, 5), segment(61, 50, phase, 2, 6, 8)), List.of(
                allocation(70, 60, phase.requirements().get(0), person, 1, "1"),
                allocation(71, 61, phase.requirements().get(0), person, 1, "1")));

        ValidationResult splitReadiness = validator.validateInput(input, null, at(0));
        assertThat(splitReadiness.validationStatus()).as(splitReadiness.problems().toString()).isEqualTo(ValidationResult.Status.PASS);
        assertThat(validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PLAN_CANDIDATE, at(9)).validationStatus()).isEqualTo(ValidationResult.Status.PASS);
    }

    @Test
    void overlappingExclusiveAllocationIsRejectedEvenIfCandidateClaimsFeasible()
    {
        Resource person = person(1, true, "1");
        Task first = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        Task second = task(11, 21, 31, false, 3600, 1, List.of(person.resourceId()));
        SolverInput input = input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(first, second), List.of());
        PlanCandidate candidate = candidate(List.of(job(50, first, 2, 3), job(51, second, 2, 3)), List.of(
                segment(60, 50, first.phases().get(0), 1, 2, 3),
                segment(61, 51, second.phases().get(0), 1, 2, 3)), List.of(
                allocation(70, 60, first.phases().get(0).requirements().get(0), person, 1, "1"),
                allocation(71, 61, second.phases().get(0).requirements().get(0), person, 1, "1")));

        assertThat(validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PLAN_CANDIDATE, at(4)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.RESOURCE_OVERLAP);
    }

    @Test
    void samePersonCannotFillTwoRequiredSeats()
    {
        Resource first = person(1, true, "1"), second = person(2, true, "1");
        Task task = task(10, 20, 30, false, 3600, 2, List.of(first.resourceId(), second.resourceId()));
        SolverInput input = input(List.of(first, second), List.of(window(40, first.resourceId(), 1, 9, "1"),
                window(41, second.resourceId(), 1, 9, "1")), List.of(task), List.of());
        Phase phase = task.phases().get(0);
        PlanCandidate candidate = candidate(List.of(job(50, task, 2, 3)), List.of(segment(60, 50, phase, 1, 2, 3)), List.of(
                allocation(70, 60, phase.requirements().get(0), first, 1, "1"),
                allocation(71, 60, phase.requirements().get(0), first, 2, "1")));

        assertThat(validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PLAN_CANDIDATE, at(4)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.NO_QUALIFIED_RESOURCE);
    }

    @Test
    void cumulativeCapacityAndPrecedenceAndFreshnessAreIndependentlyChecked()
    {
        Resource pool = person(1, false, "2");
        Task first = task(10, 20, 30, false, 3600, 1, List.of(pool.resourceId()));
        Task second = task(11, 21, 31, false, 3600, 1, List.of(pool.resourceId()));
        Dependency dependency = new Dependency(id(80), first.taskId(), second.taskId(), RelationType.FINISH_TO_START,
                0, LagBasis.ELAPSED, null, null, null, null, false);
        SolverInput input = input(List.of(pool), List.of(window(40, pool.resourceId(), 1, 9, "2")),
                List.of(first, second), List.of(dependency));
        PlanCandidate candidate = candidate(List.of(job(50, first, 2, 3), job(51, second, 2, 3)), List.of(
                segment(60, 50, first.phases().get(0), 1, 2, 3),
                segment(61, 51, second.phases().get(0), 1, 2, 3)), List.of(
                allocation(70, 60, first.phases().get(0).requirements().get(0), pool, 1, "1.5"),
                allocation(71, 61, second.phases().get(0).requirements().get(0), pool, 1, "1.5")));

        assertThat(validator.validateCandidate(input, candidate, HASH, 8, 3,
                ValidationResult.Scope.PUBLISH_PRECHECK, at(4)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.CAPACITY_EXCEEDED, ReasonCode.PRECEDENCE_VIOLATION, ReasonCode.STALE_INPUT);
    }

    @Test
    void sameStartIsPreservedButFailsInputReadiness()
    {
        Resource person = person(1, true, "1");
        Task first = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        Task second = task(11, 21, 31, false, 3600, 1, List.of(person.resourceId()));
        Dependency sync = new Dependency(id(80), first.taskId(), second.taskId(), RelationType.SAME_START,
                0, LagBasis.ELAPSED, null, null, null, null, false);
        SolverInput input = input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(first, second), List.of(sync));

        assertThat(validator.validateInput(input, null, at(0)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.UNSUPPORTED_SYNC_RULE);
    }

    @Test
    void quantityBatchAndUnknownOccupancyFailuresAreObjectLevelProblems()
    {
        Resource person = person(1, true, "1");
        Task first = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        Task second = task(11, 21, 31, false, 3600, 1, List.of(person.resourceId()));
        Dependency quantity = new Dependency(id(80), first.taskId(), second.taskId(), RelationType.QUANTITY,
                0, LagBasis.ELAPSED, "2", null, "1", "PCS", false);
        SharedBatchCandidate batch = new SharedBatchCandidate(id(81), "MANUAL_FIXED", first.operationSpecId(),
                first.workCenterId(), "O100-HEAT", "1", "PCS", 3600,
                List.of(new SharedBatchMember(first.taskId(), "2", "PCS")));
        ActualOccupancy occupancy = new ActualOccupancy(id(82), null, id(83), null, person.resourceId(),
                ResourceType.PERSON, first.taskId(), "RUN", at(1), null, null, null, null, null,
                null, ReleaseConfidence.UNKNOWN);
        SolverInput input = withExtras(input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(first, second), List.of(quantity)), List.of(batch), List.of(), List.of(occupancy));

        assertThat(validator.validateInput(input, null, at(0)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.QUANTITY_NOT_RELEASED, ReasonCode.BATCH_INCOMPATIBLE,
                        ReasonCode.OPEN_OCCUPANCY_UNKNOWN_RELEASE);
    }

    @Test
    void duplicatePhysicalOccupancyIdIsRejectedEvenWhenResourcesDiffer()
    {
        Resource firstResource = person(1, true, "1");
        Resource secondResource = person(2, true, "1");
        Task task = task(10, 20, 30, false, 3600, 1,
                List.of(firstResource.resourceId(), secondResource.resourceId()));
        ActualOccupancy first = new ActualOccupancy(id(82), null, id(83), null,
                firstResource.resourceId(), ResourceType.PERSON, task.taskId(), "RUN", at(1), null,
                null, 3600, "1", "PCS", at(2), ReleaseConfidence.TRUSTED);
        ActualOccupancy duplicate = new ActualOccupancy(id(82), null, id(83), null,
                secondResource.resourceId(), ResourceType.PERSON, task.taskId(), "RUN", at(1), null,
                null, 3600, "1", "PCS", at(2), ReleaseConfidence.TRUSTED);
        SolverInput input = withExtras(input(List.of(firstResource, secondResource), List.of(
                window(40, firstResource.resourceId(), 1, 9, "1"),
                window(41, secondResource.resourceId(), 1, 9, "1")), List.of(task), List.of()),
                List.of(), List.of(), List.of(first, duplicate));

        assertThat(validator.validateInput(input, null, at(0)).problems()).anyMatch(problem ->
                problem.reasonCode() == ReasonCode.CONTRACT_VALIDATION_FAILED
                        && problem.objectRefs().stream().anyMatch(ref ->
                                "ACTUAL_OCCUPANCY".equals(ref.objectType()) && id(82).equals(ref.objectId())));
    }

    @Test
    void candidateThatMovesAFullLockIsRejected()
    {
        Resource person = person(1, true, "1");
        Task task = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        Job job = job(50, task, 2, 3);
        PlanLock lock = new PlanLock(id(81), "JOB", job.jobId(), "FULL", at(3), at(4),
                List.of(person.resourceId()), "人工冻结");
        SolverInput input = withExtras(input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(task), List.of()), List.of(), List.of(lock), List.of());
        PlanCandidate candidate = candidate(List.of(job), List.of(segment(60, 50, task.phases().get(0), 1, 2, 3)),
                List.of(allocation(70, 60, task.phases().get(0).requirements().get(0), person, 1, "1")));

        assertThat(validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PUBLISH_PRECHECK, at(4)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.LOCK_CONFLICT);
    }

    @Test
    void candidateThatMovesSegmentTimeOrAllocationResourceLocksIsRejected()
    {
        Resource selected = person(1, true, "1");
        Resource required = person(2, true, "1");
        Task task = task(10, 20, 30, false, 3600, 1,
                List.of(selected.resourceId(), required.resourceId()));
        Job job = job(50, task, 2, 3);
        Segment segment = segment(60, 50, task.phases().get(0), 1, 2, 3);
        Allocation allocation = allocation(70, 60, task.phases().get(0).requirements().get(0), selected, 1, "1");
        PlanLock segmentLock = new PlanLock(id(81), "SEGMENT", segment.segmentId(), "TIME", at(3), at(4),
                List.of(), "固定分段时间");
        PlanLock allocationLock = new PlanLock(id(82), "ALLOCATION", allocation.allocationId(), "RESOURCE",
                null, null, List.of(required.resourceId()), "固定分配资源");
        SolverInput input = withExtras(input(List.of(selected, required),
                List.of(window(40, selected.resourceId(), 1, 9, "1"),
                        window(41, required.resourceId(), 1, 9, "1")), List.of(task), List.of()),
                List.of(), List.of(segmentLock, allocationLock), List.of());
        PlanCandidate candidate = candidate(List.of(job), List.of(segment), List.of(allocation));

        assertThat(validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PUBLISH_PRECHECK, at(4)).problems())
                .filteredOn(problem -> problem.reasonCode() == ReasonCode.LOCK_CONFLICT)
                .hasSize(2);
    }

    @Test
    void candidateCannotOverlapTrustedActualResourceOccupancy()
    {
        Resource person = person(1, true, "1");
        Task task = task(10, 20, 30, false, 3600, 1, List.of(person.resourceId()));
        ActualOccupancy occupancy = new ActualOccupancy(id(82), null, id(83), null, person.resourceId(),
                ResourceType.PERSON, null, "RUN", at(1), at(3), null, null, null, null,
                at(3), ReleaseConfidence.TRUSTED);
        SolverInput input = withExtras(input(List.of(person), List.of(window(40, person.resourceId(), 1, 9, "1")),
                List.of(task), List.of()), List.of(), List.of(), List.of(occupancy));
        PlanCandidate candidate = candidate(List.of(job(50, task, 2, 3)),
                List.of(segment(60, 50, task.phases().get(0), 1, 2, 3)),
                List.of(allocation(70, 60, task.phases().get(0).requirements().get(0), person, 1, "1")));

        assertThat(validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PLAN_CANDIDATE, at(4)).problems()).extracting(problem -> problem.reasonCode())
                .contains(ReasonCode.RESOURCE_OVERLAP);
    }

    @Test
    void fixedTwentyTaskGoldenCandidatePassesIndependentReplay()
    {
        Resource person = person(1, true, "1");
        List<Task> tasks = new java.util.ArrayList<>();
        List<Job> jobs = new java.util.ArrayList<>();
        List<Segment> segments = new java.util.ArrayList<>();
        List<Allocation> allocations = new java.util.ArrayList<>();
        for (int index = 0; index < 20; index++)
        {
            Task task = task(1000 + index, 2000 + index, 3000 + index, false, 3600, 1,
                    List.of(person.resourceId()));
            int jobNo = 4000 + index, segmentNo = 5000 + index;
            tasks.add(task);
            jobs.add(job(jobNo, task, index + 1, index + 2));
            segments.add(segment(segmentNo, jobNo, task.phases().get(0), 1, index + 1, index + 2));
            allocations.add(allocation(6000 + index, segmentNo, task.phases().get(0).requirements().get(0),
                    person, 1, "1"));
        }
        SolverInput input = input(List.of(person), List.of(window(40, person.resourceId(), 1, 23, "1")), tasks,
                List.of());
        PlanCandidate candidate = candidate(jobs, segments, allocations);

        assertThat(validator.validateInput(input, null, at(0)).validationStatus())
                .isEqualTo(ValidationResult.Status.PASS);
        ValidationResult replay = validator.validateCandidate(input, candidate, HASH, 7, 3,
                ValidationResult.Scope.PUBLISH_PRECHECK, at(23));
        assertThat(replay.validationStatus()).as(replay.problems().toString()).isEqualTo(ValidationResult.Status.PASS);
        assertThat(replay.publishable()).isTrue();
        assertThat(replay.problems()).isEmpty();
    }

    @Test
    void prdO100ReadinessRecomputesMachinePersonQuantityAndSharedFurnaceFacts()
    {
        Resource m1 = resource(701, ResourceType.MACHINE), m2 = resource(702, ResourceType.MACHINE);
        Resource furnace = resource(703, ResourceType.MACHINE);
        Resource zhang = resource(711, ResourceType.PERSON), li = resource(712, ResourceType.PERSON);
        Resource wang = resource(713, ResourceType.PERSON), zhao = resource(714, ResourceType.PERSON);
        Task a10 = o100Task(720, 100, List.of(
                phase(721, PhaseType.RUN, 1, 4 * 3600, requirement(731, ResourceType.MACHINE, m1),
                        requirement(732, ResourceType.PERSON, zhang))));
        Task b10 = o100Task(722, 100, List.of(
                phase(723, PhaseType.RUN, 1, 2 * 3600, requirement(733, ResourceType.MACHINE, m2),
                        requirement(734, ResourceType.PERSON, li))));
        List<Phase> heatPhasesA = List.of(
                phase(724, PhaseType.SETUP, 1, 1800, requirement(735, ResourceType.PERSON, wang)),
                phase(725, PhaseType.RUN, 2, 7200, requirement(736, ResourceType.MACHINE, furnace)),
                phase(726, PhaseType.UNLOAD, 3, 1800, requirement(737, ResourceType.PERSON, wang)));
        List<Phase> heatPhasesB = heatPhasesA;
        Task a20 = o100Task(730, 100, heatPhasesA);
        Task b20Source = o100Task(741, 100, heatPhasesB);
        Task b20 = new Task(b20Source.taskId(), b20Source.orderLineId(), a20.operationSpecId(),
                b20Source.workCenterId(), b20Source.planningClass(), b20Source.quantity(), b20Source.uomCode(),
                b20Source.earliestStartAt(), b20Source.promisedAt(), b20Source.phases());
        Task a30 = o100Task(742, 100, List.of(phase(743, PhaseType.RUN, 1, 3600,
                requirement(744, ResourceType.PERSON, zhao))));
        Task b30 = o100Task(745, 100, List.of(phase(746, PhaseType.RUN, 1, 3600,
                requirement(747, ResourceType.PERSON, zhao))));
        List<Resource> resources = List.of(m1, m2, furnace, zhang, li, wang, zhao);
        List<AvailabilityWindow> windows = new java.util.ArrayList<>();
        for (int index = 0; index < resources.size(); index++)
            windows.add(window(760 + index, resources.get(index).resourceId(), 0, 24, "1"));
        List<Dependency> dependencies = List.of(
                new Dependency(id(770), a10.taskId(), b10.taskId(), RelationType.QUANTITY, 0,
                        LagBasis.ELAPSED, "50", null, "20", "PCS", false),
                finish(771, a10, a20), finish(772, b10, b20), finish(773, a20, a30), finish(774, b20, b30));
        SharedBatchCandidate sharedFurnace = new SharedBatchCandidate(id(775), "MANUAL_FIXED",
                a20.operationSpecId(), a20.workCenterId(), "O100-FURNACE-A60-B40", "200", "PCS", 10800,
                List.of(new SharedBatchMember(a20.taskId(), "100", "PCS"),
                        new SharedBatchMember(b20.taskId(), "100", "PCS")));
        SolverInput input = withExtras(input(resources, windows, List.of(a10, b10, a20, b20, a30, b30), dependencies),
                List.of(sharedFurnace), List.of(), List.of());

        ValidationResult readiness = validator.validateInput(input, null, at(0));

        assertThat(readiness.validationStatus()).as(readiness.problems().toString()).isEqualTo(ValidationResult.Status.PASS);
        assertThat(readiness.problems()).isEmpty();
    }

    private SolverInput input(List<Resource> resources, List<AvailabilityWindow> windows,
            List<Task> tasks, List<Dependency> dependencies)
    {
        return new SolverInput("1.0", "SOLVER_INPUT", id(90), id(91), at(0), 7, 3, HASH,
                "SHA-256", "JCS-RFC8785", "aps-cpsat-v1", new Scope("SITE_01", List.of(id(92))),
                new Horizon(at(0), at(12), at(24), at(0), 60, "Asia/Shanghai"), null,
                new Parameters("FORWARD", 30, 1, 1, 0, 0), resources, windows, tasks, dependencies,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SolverInput withExtras(SolverInput source, List<SharedBatchCandidate> batches,
            List<PlanLock> locks, List<ActualOccupancy> occupancies)
    {
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(), source.planVersionId(),
                source.capturedAt(), source.definitionRevision(), source.executionRevision(), source.inputHash(),
                source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(), source.horizon(),
                source.baseVersion(), source.parameters(), source.resources(), source.availabilityWindows(), source.tasks(),
                source.dependencies(), source.materialSupplies(), source.materialDemands(), batches, locks, occupancies);
    }

    private Resource person(int no, boolean exclusive, String capacity)
    {
        return new Resource(id(no), ResourceType.PERSON, id(99), exclusive, capacity, null,
                List.of(new ResourceSkill(id(100 + no), "ASSEMBLY", 3, at(-24), null, "ACTIVE")));
    }

    private Resource resource(int no, ResourceType type)
    {
        return new Resource(id(no), type, id(99), true, "1", null, List.of());
    }

    private Task o100Task(int no, int quantity, List<Phase> phases)
    {
        return new Task(id(no), id(800 + no), id(1600 + no), id(99), PlanningClass.MANDATORY_DETAIL,
                Integer.toString(quantity), "PCS", at(1), at(23), phases);
    }

    private Phase phase(int no, PhaseType type, int sequence, int seconds, ResourceRequirement... requirements)
    {
        return new Phase(id(no), type, sequence, seconds, "0", false, 1, 0, List.of(requirements));
    }

    private ResourceRequirement requirement(int no, ResourceType type, Resource resource)
    {
        return new ResourceRequirement(id(no), type, 1, "1", null, null, List.of(resource.resourceId()));
    }

    private Dependency finish(int no, Task predecessor, Task successor)
    {
        return new Dependency(id(no), predecessor.taskId(), successor.taskId(), RelationType.FINISH_TO_START,
                0, LagBasis.ELAPSED, null, null, null, null, false);
    }

    private AvailabilityWindow window(int no, String resourceId, int startHour, int endHour, String capacity)
    {
        return new AvailabilityWindow(id(no), resourceId, at(startHour), at(endHour), capacity);
    }

    private Task task(int taskNo, int phaseNo, int requirementNo, boolean interruptible,
            int durationSeconds, int seats, List<String> candidates)
    {
        ResourceRequirement requirement = new ResourceRequirement(id(requirementNo), ResourceType.PERSON,
                seats, "1", "ASSEMBLY", 3, candidates);
        Phase phase = new Phase(id(phaseNo), PhaseType.RUN, 1, durationSeconds, "0", interruptible,
                interruptible ? 2 : 1, interruptible ? Math.max(1, durationSeconds / 4) : 0,
                0, SolverInput.SegmentResourcePolicy.SAME_RESOURCES, List.of(requirement));
        return new Task(id(taskNo), id(200 + taskNo), id(300 + taskNo), id(99), PlanningClass.MANDATORY_DETAIL,
                "1", "PCS", at(1), at(10), List.of(phase));
    }

    private Job job(int no, Task task, int start, int end)
    {
        return new Job(id(no), "NORMAL", task.operationSpecId(), task.workCenterId(), task.quantity(), task.uomCode(),
                at(start), at(end), List.of(task.taskId()));
    }

    private Segment segment(int no, int jobNo, Phase phase, int segmentNo, int start, int end)
    {
        return new Segment(id(no), id(jobNo), phase.phaseId(), phase.phaseType(), segmentNo,
                at(start), at(end), "1", null, null);
    }

    private Allocation allocation(int no, int segmentNo, ResourceRequirement requirement,
            Resource resource, int seat, String capacity)
    {
        return new Allocation(id(no), id(segmentNo), requirement.requirementId(), resource.resourceId(),
                resource.resourceType(), seat, capacity);
    }

    private PlanCandidate candidate(List<Job> jobs, List<Segment> segments, List<Allocation> allocations)
    {
        return new PlanCandidate(at(0), at(24), jobs, segments, allocations, List.of(), List.of());
    }

    private Instant at(int hour) { return Instant.parse("2026-09-15T00:00:00Z").plusSeconds(hour * 3600L); }
    private String id(int no) { return String.format("00000000-0000-4000-8000-%012d", no); }
}
