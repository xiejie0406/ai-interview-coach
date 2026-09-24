package com.ruoyi.aps.application.planning;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.foundation.ApsValidationException;
import com.ruoyi.aps.application.foundation.ApsValidationIssue;
import com.ruoyi.aps.application.order.OrderCatalog.ProductionLot;
import com.ruoyi.aps.application.order.OrderCatalog.ProductionOrder;
import com.ruoyi.aps.application.order.OrderCatalog.Task;
import com.ruoyi.aps.application.order.OrderManagementService;
import com.ruoyi.aps.application.order.OrderRepository;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.ValidationResult;

/**
 * 插单、拆批和固定合批的版本化入口。结构事实与 M19 草稿在同一 SERIALIZABLE 事务内完成，
 * 原正式计划保持不可变；失败时拆批数量及新任务一并回滚。
 */
public final class PlanStructuralAdjustmentService
{
    private final PlanRepository plans;
    private final PlanWorkbenchService workbench;
    private final OrderRepository orders;
    private final OrderManagementService orderManagement;
    private final SolverInputCompiler compiler;
    private final ApsTransactionOperations transactions;
    private final ImpactClosureService impacts = new ImpactClosureService();

    public PlanStructuralAdjustmentService(PlanRepository plans, PlanWorkbenchService workbench,
            OrderRepository orders, OrderManagementService orderManagement, SolverInputCompiler compiler,
            ApsTransactionOperations transactions)
    {
        this.plans = Objects.requireNonNull(plans);
        this.workbench = Objects.requireNonNull(workbench);
        this.orders = Objects.requireNonNull(orders);
        this.orderManagement = Objects.requireNonNull(orderManagement);
        this.compiler = Objects.requireNonNull(compiler);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public CreatedStructuralAdjustment create(ResourceAccessScope access, StructuralAdjustment command,
            String actor)
    {
        NormalizedCommand normalized = normalize(command);
        String fingerprint = fingerprint(normalized);
        return transactions.serializable(() -> {
            PlanRepository.PlanDetail source = workbench.detail(access, normalized.basePlanVersionId());
            PlanRepository.PlanRecord existing = plans.findByRequestId(normalized.requestId()).orElse(null);
            if (existing != null)
            {
                requireMatchingReplay(existing, normalized, fingerprint);
                SolverInput existingInput = plans.findRequestSnapshot(normalized.requestId())
                        .map(PlanRepository.PlanRequestSnapshot::input)
                        .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.CONFLICT,
                                "结构调整草稿缺少冻结输入"));
                List<String> seeds = replaySeeds(normalized, existingInput);
                return result(existing, true, source, existingInput, seeds,
                        normalized.action() == Action.SPLIT_LOT ? derivedLotId(normalized.requestId()) : null);
            }

            PlanRepository.PlanRecord locked = plans.findPlanForUpdate(source.plan().id())
                    .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.NOT_FOUND, "正式计划不存在"));
            requireCurrentPublished(source, locked, normalized.expectedBaseRowVersion());
            if (source.input() == null || source.candidateHash() == null)
                throw new ApsBusinessException(ApsErrorCode.CONFLICT, "当前正式计划缺少冻结输入或候选摘要");

            List<String> orderIds = new ArrayList<>(sourceOrderIds(source.input()));
            List<SolverInputCompiler.SharedBatchRequest> batches = sourceBatches(source.input());
            ActionResult action = apply(normalized, source, orderIds, batches, actor);
            Instant capturedAt = max(normalized.capturedAt(), plans.latestPlanningFactUpdatedAt()
                    .orElse(normalized.capturedAt()));
            String nextPlanId = stableId("structural-plan", normalized.requestId());
            SolverInputCompiler.CompileRequest request = compileRequest(source, normalized, nextPlanId,
                    orderIds, batches, action.changedTaskIds(), capturedAt);
            SolverInputCompiler.Compilation compilation = compiler.compile(access, request);
            requireReady(compilation.readiness(), nextPlanId);

            PlanRepository.PlanRecord plan = new PlanRepository.PlanRecord(nextPlanId, source.plan().id(),
                    plans.nextVersionNo(), action.title() + "-" + normalized.requestId().substring(0, 8),
                    normalized.requestId(), compilation.input().definitionRevision(),
                    compilation.input().executionRevision(), compilation.input().inputHash(), "DRAFT",
                    capturedAt, capturedAt, 0);
            plans.insertDraft(plan, compilation.json(), fingerprint, normalized.reason(), actor);
            return result(plan, false, source, compilation.input(), action.changedTaskIds(),
                    action.derivedLotId());
        });
    }

    private ActionResult apply(NormalizedCommand command, PlanRepository.PlanDetail source,
            List<String> orderIds, List<SolverInputCompiler.SharedBatchRequest> batches, String actor)
    {
        Set<String> sourceTaskIds = source.input().tasks().stream().map(SolverInput.Task::taskId)
                .collect(java.util.stream.Collectors.toSet());
        return switch (command.action())
        {
            case INSERT_ORDER -> insertOrder(command, orderIds, sourceTaskIds);
            case SPLIT_LOT -> splitLot(command, source, sourceTaskIds, actor);
            case MERGE_BATCH -> mergeBatch(command, sourceTaskIds, batches);
        };
    }

    private ActionResult insertOrder(NormalizedCommand command, List<String> orderIds, Set<String> sourceTaskIds)
    {
        if (orderIds.contains(command.orderId()))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "插单订单已经在当前正式计划范围内");
        ProductionOrder order = orders.findOrder(command.orderId())
                .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.NOT_FOUND, "插单订单不存在"));
        if (!"RELEASED".equals(order.status()))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "只有已释放订单可以插入排程");
        List<String> taskIds = orders.listTasks(order.id()).stream().map(Task::id).sorted().toList();
        if (taskIds.isEmpty() || taskIds.stream().anyMatch(sourceTaskIds::contains))
            throw new ApsBusinessException(ApsErrorCode.CONFLICT, "插单订单任务网络为空或与当前范围发生身份冲突");
        orderIds.add(order.id());
        orderIds.sort(String::compareTo);
        return new ActionResult("插单草稿", taskIds, null);
    }

    private ActionResult splitLot(NormalizedCommand command, PlanRepository.PlanDetail source,
            Set<String> sourceTaskIds, String actor)
    {
        ProductionLot parent = orders.findLot(command.parentLotId())
                .orElseThrow(() -> new ApsBusinessException(ApsErrorCode.NOT_FOUND, "待拆生产批不存在"));
        List<Task> parentTasks = orders.listTasksByLot(parent.id());
        List<String> parentTaskIds = parentTasks.stream().map(Task::id).sorted().toList();
        if (parentTaskIds.isEmpty() || !sourceTaskIds.containsAll(parentTaskIds))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "待拆生产批不完整属于当前正式计划范围");
        Set<String> batched = source.input().sharedBatchCandidates().stream()
                .flatMap(batch -> batch.members().stream()).map(SolverInput.SharedBatchMember::taskId)
                .collect(java.util.stream.Collectors.toSet());
        if (parentTaskIds.stream().anyMatch(batched::contains))
            throw new ApsBusinessException(ApsErrorCode.CONFLICT,
                    "待拆生产批已经属于固定共享批，P0 必须先形成不含该共享批的正式基线");
        requireNotStarted(parentTaskIds);
        String lotId = derivedLotId(command.requestId());
        orderManagement.createPlanningSplitLot(parent.id(), command.splitQuantity(), command.reason(),
                lotId, actor);
        List<String> changed = new ArrayList<>(parentTaskIds);
        changed.addAll(orders.listTasksByLot(lotId).stream().map(Task::id).toList());
        changed = changed.stream().distinct().sorted().toList();
        return new ActionResult("拆批草稿", changed, lotId);
    }

    private ActionResult mergeBatch(NormalizedCommand command, Set<String> sourceTaskIds,
            List<SolverInputCompiler.SharedBatchRequest> batches)
    {
        List<String> memberIds = command.members().stream().map(BatchMember::taskId).sorted().toList();
        if (!sourceTaskIds.containsAll(memberIds))
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "合批成员必须全部属于当前正式计划范围");
        requireNotStarted(memberIds);
        batches.add(new SolverInputCompiler.SharedBatchRequest(command.compatibilityKey(), command.members().stream()
                .map(member -> new SolverInputCompiler.SharedBatchMemberRequest(member.taskId(), member.quantity()))
                .toList()));
        return new ActionResult("合批草稿", memberIds, null);
    }

    private void requireNotStarted(List<String> taskIds)
    {
        List<String> started = plans.findStartedTaskIds(taskIds);
        if (!started.isEmpty())
            throw new ApsBusinessException(ApsErrorCode.CONFLICT,
                    "已开始执行的任务不能拆批或改变共享批成员：" + String.join("、", started));
    }

    private List<String> sourceOrderIds(SolverInput input)
    {
        Set<String> remaining = input.tasks().stream().map(SolverInput.Task::taskId)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (ProductionOrder order : orders.listOrders().stream().sorted(Comparator.comparing(ProductionOrder::id)).toList())
        {
            Set<String> taskIds = orders.listTasks(order.id()).stream().map(Task::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (taskIds.stream().anyMatch(remaining::contains))
            {
                selected.add(order.id());
                remaining.removeAll(taskIds);
            }
        }
        if (!remaining.isEmpty())
            throw new ApsBusinessException(ApsErrorCode.CONFLICT,
                    "正式计划任务无法完整映射回订单范围：" + String.join("、", remaining.stream().sorted().toList()));
        return selected.stream().toList();
    }

    private List<SolverInputCompiler.SharedBatchRequest> sourceBatches(SolverInput input)
    {
        return new ArrayList<>(input.sharedBatchCandidates().stream().map(batch ->
                new SolverInputCompiler.SharedBatchRequest(batch.compatibilityKey(), batch.members().stream()
                        .map(member -> new SolverInputCompiler.SharedBatchMemberRequest(member.taskId(),
                                new BigDecimal(member.quantity()))).toList())).toList());
    }

    private SolverInputCompiler.CompileRequest compileRequest(PlanRepository.PlanDetail source,
            NormalizedCommand command, String planId, List<String> orderIds,
            List<SolverInputCompiler.SharedBatchRequest> batches, List<String> changedTaskIds, Instant capturedAt)
    {
        SolverInput input = source.input();
        SolverInput.Horizon horizon = input.horizon();
        SolverInput.Parameters parameters = input.parameters();
        return new SolverInputCompiler.CompileRequest(command.requestId(), planId, input.scope().siteCode(),
                input.scope().workshopIds(), orderIds, capturedAt,
                Math.addExact(input.definitionRevision(), 1), input.executionRevision(), horizon.startAt(),
                horizon.detailEndAt(), horizon.endAt(), horizon.planningAnchorAt(), horizon.timeUnitSeconds(),
                horizon.displayTimeZone(), input.modelVersion(), parameters.maxSolveSeconds(),
                parameters.randomSeed(), parameters.solverSearchThreads(), parameters.absoluteGapLimit(),
                parameters.relativeGapLimit(), batches, new SolverInputCompiler.BaseVersionRequest(
                        source.plan().id(), source.plan().inputHash(), horizon.planningAnchorAt(), changedTaskIds));
    }

    private CreatedStructuralAdjustment result(PlanRepository.PlanRecord plan, boolean reused,
            PlanRepository.PlanDetail source, SolverInput input, List<String> seeds, String lotId)
    {
        ImpactClosureService.ImpactClosure impact = impacts.calculate(input, PlanCandidateProjection.from(source),
                Set.copyOf(seeds), Set.of());
        return new CreatedStructuralAdjustment(plan, reused, source.candidateHash(), impact, lotId);
    }

    private List<String> replaySeeds(NormalizedCommand command, SolverInput input)
    {
        return switch (command.action())
        {
            case INSERT_ORDER -> {
                Set<String> taskIds = orders.listTasks(command.orderId()).stream().map(Task::id)
                        .collect(java.util.stream.Collectors.toSet());
                yield input.tasks().stream().map(SolverInput.Task::taskId).filter(taskIds::contains).sorted().toList();
            }
            case SPLIT_LOT -> {
                List<String> ids = new ArrayList<>(orders.listTasksByLot(command.parentLotId()).stream()
                        .map(Task::id).toList());
                ids.addAll(orders.listTasksByLot(derivedLotId(command.requestId())).stream().map(Task::id).toList());
                yield ids.stream().distinct().sorted().toList();
            }
            case MERGE_BATCH -> command.members().stream().map(BatchMember::taskId).sorted().toList();
        };
    }

    private void requireCurrentPublished(PlanRepository.PlanDetail source, PlanRepository.PlanRecord locked,
            long expectedRowVersion)
    {
        if (!"PUBLISHED".equals(locked.status())
                || plans.findCurrentPublishedBaseline(locked.id()).isEmpty())
            throw new ApsBusinessException(ApsErrorCode.CONFLICT,
                    "结构调整必须基于当前系统内正式计划");
        if (locked.rowVersion() != expectedRowVersion || source.plan().rowVersion() != expectedRowVersion)
            throw new ApsBusinessException(ApsErrorCode.STALE_VERSION, "正式计划版本已变化，请刷新后重试");
    }

    private void requireMatchingReplay(PlanRepository.PlanRecord existing, NormalizedCommand command,
            String fingerprint)
    {
        String storedFingerprint = plans.findRequestFingerprintByRequestId(command.requestId()).orElse("");
        if (!Objects.equals(existing.baseVersionId(), command.basePlanVersionId())
                || !Objects.equals(storedFingerprint, fingerprint))
            throw new ApsBusinessException(ApsErrorCode.IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 已绑定不同结构调整意图");
    }

    private void requireReady(ValidationResult readiness, String planId)
    {
        if (readiness.validationStatus() == ValidationResult.Status.PASS) return;
        throw new ApsValidationException(ApsErrorCode.INVALID_REQUEST, "结构调整后的求解输入尚未通过数据就绪检查",
                readiness.problems().stream().map(problem -> {
                    var ref = problem.objectRefs().isEmpty() ? null : problem.objectRefs().get(0);
                    return new ApsValidationIssue(problem.reasonCode().name(),
                            ref == null ? "PLAN_VERSION" : ref.objectType(),
                            ref == null ? planId : ref.objectId(), ref == null ? null : ref.field(),
                            problem.detail());
                }).toList());
    }

    private NormalizedCommand normalize(StructuralAdjustment command)
    {
        if (command == null || command.action() == null || !uuid(command.requestId())
                || !uuid(command.basePlanVersionId()) || command.expectedBaseRowVersion() < 0
                || command.capturedAt() == null)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "结构调整请求不完整");
        String reason = required(command.reason(), 500, "结构调整原因不能为空");
        return switch (command.action())
        {
            case INSERT_ORDER -> {
                requireOnly(command, true, false, false);
                yield new NormalizedCommand(command.requestId(), command.basePlanVersionId(),
                        command.expectedBaseRowVersion(), command.capturedAt(), command.action(),
                        requiredUuid(command.orderId(), "插单订单 ID 无效"), null, null, null, List.of(), reason);
            }
            case SPLIT_LOT -> {
                requireOnly(command, false, true, false);
                if (command.splitQuantity() == null || command.splitQuantity().signum() <= 0)
                    throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "拆批数量必须大于零");
                yield new NormalizedCommand(command.requestId(), command.basePlanVersionId(),
                        command.expectedBaseRowVersion(), command.capturedAt(), command.action(), null,
                        requiredUuid(command.parentLotId(), "待拆生产批 ID 无效"),
                        command.splitQuantity().stripTrailingZeros(), null, List.of(), reason);
            }
            case MERGE_BATCH -> {
                requireOnly(command, false, false, true);
                String key = required(command.compatibilityKey(), 128, "合批兼容键不能为空");
                List<BatchMember> members = command.members() == null ? List.of() : command.members().stream()
                        .map(member -> {
                            if (member == null || !uuid(member.taskId()) || member.quantity() == null
                                    || member.quantity().signum() <= 0)
                                throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST,
                                        "合批成员必须提供任务 UUID 和正数量");
                            return new BatchMember(member.taskId(), member.quantity().stripTrailingZeros());
                        }).sorted(Comparator.comparing(BatchMember::taskId)).toList();
                if (members.size() < 2 || members.size() > 100
                        || members.stream().map(BatchMember::taskId).distinct().count() != members.size())
                    throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "合批必须包含 2 到 100 个不重复任务");
                yield new NormalizedCommand(command.requestId(), command.basePlanVersionId(),
                        command.expectedBaseRowVersion(), command.capturedAt(), command.action(), null, null,
                        null, key, members, reason);
            }
        };
    }

    private void requireOnly(StructuralAdjustment command, boolean order, boolean split, boolean batch)
    {
        boolean hasOrder = command.orderId() != null && !command.orderId().isBlank();
        boolean hasSplit = command.parentLotId() != null && !command.parentLotId().isBlank()
                || command.splitQuantity() != null;
        boolean hasBatch = command.compatibilityKey() != null && !command.compatibilityKey().isBlank()
                || command.members() != null && !command.members().isEmpty();
        if (hasOrder != order || hasSplit != split || hasBatch != batch)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST,
                    "结构调整只能提交当前动作所需字段，不能混合多个意图");
    }

    private String fingerprint(NormalizedCommand command)
    {
        String members = command.members().stream().map(member -> member.taskId() + "=" + decimal(member.quantity()))
                .collect(java.util.stream.Collectors.joining(","));
        String canonical = String.join("\u0000", command.basePlanVersionId(),
                Long.toString(command.expectedBaseRowVersion()), command.capturedAt().toString(),
                command.action().name(), value(command.orderId()), value(command.parentLotId()),
                command.splitQuantity() == null ? "" : decimal(command.splitQuantity()),
                value(command.compatibilityKey()), members, command.reason());
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private String required(String value, int max, String message)
    {
        if (value == null || value.isBlank()) throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message);
        String normalized = value.trim();
        if (normalized.length() > max)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message.replace("不能为空", "长度超限"));
        return normalized;
    }

    private String requiredUuid(String value, String message)
    {
        if (!uuid(value)) throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, message);
        return value;
    }

    private boolean uuid(String value)
    {
        try { UUID.fromString(value); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    private String stableId(String... parts)
    {
        return UUID.nameUUIDFromBytes(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String derivedLotId(String requestId) { return stableId("structural-split-lot", requestId); }
    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private String value(String value) { return value == null ? "" : value; }
    private Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }

    private record NormalizedCommand(String requestId, String basePlanVersionId, long expectedBaseRowVersion,
            Instant capturedAt, Action action, String orderId, String parentLotId, BigDecimal splitQuantity,
            String compatibilityKey, List<BatchMember> members, String reason) { }
    private record ActionResult(String title, List<String> changedTaskIds, String derivedLotId) { }

    public enum Action { INSERT_ORDER, SPLIT_LOT, MERGE_BATCH }
    public record BatchMember(String taskId, BigDecimal quantity) { }
    public record StructuralAdjustment(String requestId, String basePlanVersionId, long expectedBaseRowVersion,
            Instant capturedAt, Action action, String orderId, String parentLotId, BigDecimal splitQuantity,
            String compatibilityKey, List<BatchMember> members, String reason) { }
    public record CreatedStructuralAdjustment(PlanRepository.PlanRecord plan, boolean reused,
            String baseCandidateHash, ImpactClosureService.ImpactClosure impact, String derivedLotId) { }
}
