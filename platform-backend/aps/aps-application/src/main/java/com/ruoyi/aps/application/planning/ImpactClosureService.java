package com.ruoyi.aps.application.planning;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.ruoyi.aps.solver.contract.PlanCandidate;
import com.ruoyi.aps.solver.contract.SolverInput;

/**
 * 计算局部重排的确定性影响闭包。闭包只决定“必须一起重排/复验”的范围，不绕过全量独立校验。
 */
public final class ImpactClosureService
{
    public ImpactClosure calculate(SolverInput input, PlanCandidate candidate, Set<String> changedTaskIds,
            Set<String> changedResourceIds)
    {
        if (input == null || candidate == null) throw new IllegalArgumentException("求解输入和当前候选计划不能为空");
        Set<String> requestedTasks = changedTaskIds == null ? Set.of() : Set.copyOf(changedTaskIds);
        Set<String> requestedResources = changedResourceIds == null ? Set.of() : Set.copyOf(changedResourceIds);
        Map<String, SolverInput.Task> tasks = input.tasks().stream().collect(java.util.stream.Collectors.toMap(
                SolverInput.Task::taskId, value -> value));
        Set<String> resources = input.resources().stream().map(SolverInput.Resource::resourceId)
                .collect(java.util.stream.Collectors.toSet());
        if (!tasks.keySet().containsAll(requestedTasks)) throw new IllegalArgumentException("变更范围包含未知任务");
        if (!resources.containsAll(requestedResources)) throw new IllegalArgumentException("变更范围包含未知资源");

        Map<String, Set<TaskEdge>> taskEdges = new HashMap<>();
        input.dependencies().forEach(value -> connect(taskEdges, value.predecessorTaskId(), value.successorTaskId(),
                "DEPENDENCY:" + value.dependencyId()));
        input.materialDemands().stream().filter(value -> value.sourceTaskId() != null).forEach(value ->
                connect(taskEdges, value.sourceTaskId(), value.targetTaskId(), "MATERIAL:" + value.demandId()));
        input.sharedBatchCandidates().forEach(value -> connectAll(taskEdges,
                value.members().stream().map(SolverInput.SharedBatchMember::taskId).toList(),
                "SHARED_BATCH:" + value.candidateId()));
        tasks.values().stream().collect(java.util.stream.Collectors.groupingBy(SolverInput.Task::orderLineId))
                .forEach((orderLineId, members) -> connectAll(taskEdges,
                        members.stream().map(SolverInput.Task::taskId).toList(), "ORDER_LINE:" + orderLineId));

        Map<String, PlanCandidate.Job> jobs = candidate.jobs().stream().collect(java.util.stream.Collectors.toMap(
                PlanCandidate.Job::jobId, value -> value));
        Map<String, String> jobBySegment = candidate.segments().stream().collect(java.util.stream.Collectors.toMap(
                PlanCandidate.Segment::segmentId, PlanCandidate.Segment::jobId));
        Map<String, Set<String>> resourcesByTask = new HashMap<>();
        Map<String, Set<String>> tasksByResource = new HashMap<>();
        // 结构调整后的新增任务尚无候选分配，仍必须把所有合格候选资源及其竞争任务纳入闭包。
        // 已排候选分配会在下方再次连接；Set 可保证同一关系不重复。
        tasks.values().forEach(task -> task.phases().forEach(phase -> phase.requirements().forEach(requirement ->
                requirement.candidateResourceIds().forEach(resourceId -> {
                    if (resources.contains(resourceId))
                        connectTaskResource(resourcesByTask, tasksByResource, task.taskId(), resourceId);
                }))));
        candidate.allocations().forEach(allocation -> {
            PlanCandidate.Job job = jobs.get(jobBySegment.get(allocation.segmentId()));
            if (job == null) return;
            for (String taskId : job.memberTaskIds())
            {
                resourcesByTask.computeIfAbsent(taskId, ignored -> new TreeSet<>()).add(allocation.resourceId());
                tasksByResource.computeIfAbsent(allocation.resourceId(), ignored -> new TreeSet<>()).add(taskId);
            }
        });
        candidate.jobs().stream().filter(job -> job.memberTaskIds().size() > 1).forEach(job ->
                connectAll(taskEdges, job.memberTaskIds(), "PLAN_JOB:" + job.jobId()));
        input.locks().forEach(lock -> {
            if ("TASK".equals(lock.targetType()) && tasks.containsKey(lock.targetId()))
                lock.lockedResourceIds().forEach(resourceId -> connectTaskResource(resourcesByTask, tasksByResource,
                        lock.targetId(), resourceId));
            if ("JOB".equals(lock.targetType()) && jobs.containsKey(lock.targetId()))
                jobs.get(lock.targetId()).memberTaskIds().forEach(taskId -> lock.lockedResourceIds().forEach(resourceId ->
                        connectTaskResource(resourcesByTask, tasksByResource, taskId, resourceId)));
        });

        LinkedHashSet<String> affectedTasks = new LinkedHashSet<>();
        LinkedHashSet<String> affectedResources = new LinkedHashSet<>();
        Map<String, Set<String>> reasons = new HashMap<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        requestedTasks.stream().sorted().forEach(id -> {
            affectedTasks.add(id); addReason(reasons, "TASK:" + id, "CHANGED_TASK"); queue.add(new Node(true, id));
        });
        requestedResources.stream().sorted().forEach(id -> {
            affectedResources.add(id); addReason(reasons, "RESOURCE:" + id, "CHANGED_RESOURCE"); queue.add(new Node(false, id));
        });
        while (!queue.isEmpty())
        {
            Node node = queue.removeFirst();
            if (node.task())
            {
                taskEdges.getOrDefault(node.id(), Set.of()).stream()
                        .sorted(Comparator.comparing(TaskEdge::taskId).thenComparing(TaskEdge::reason))
                        .forEach(edge -> {
                            addReason(reasons, "TASK:" + edge.taskId(), edge.reason());
                            if (affectedTasks.add(edge.taskId())) queue.add(new Node(true, edge.taskId()));
                        });
                resourcesByTask.getOrDefault(node.id(), Set.of()).forEach(resourceId -> {
                    addReason(reasons, "RESOURCE:" + resourceId, "RESOURCE_ALLOCATION:TASK:" + node.id());
                    if (affectedResources.add(resourceId)) queue.add(new Node(false, resourceId));
                });
            }
            else
            {
                tasksByResource.getOrDefault(node.id(), Set.of()).forEach(taskId -> {
                    addReason(reasons, "TASK:" + taskId, "RESOURCE_ALLOCATION:RESOURCE:" + node.id());
                    if (affectedTasks.add(taskId)) queue.add(new Node(true, taskId));
                });
            }
        }
        Map<String, List<String>> stableReasons = new LinkedHashMap<>();
        reasons.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                stableReasons.put(entry.getKey(), entry.getValue().stream().sorted().toList()));
        return new ImpactClosure(affectedTasks.stream().sorted().toList(), affectedResources.stream().sorted().toList(),
                stableReasons, true);
    }

    private void connectAll(Map<String, Set<TaskEdge>> edges, List<String> members, String reason)
    {
        List<String> stable = members.stream().distinct().sorted().toList();
        for (int left = 0; left < stable.size(); left++)
            for (int right = left + 1; right < stable.size(); right++)
                connect(edges, stable.get(left), stable.get(right), reason);
    }

    private void connect(Map<String, Set<TaskEdge>> edges, String left, String right, String reason)
    {
        if (left == null || right == null || left.equals(right)) return;
        edges.computeIfAbsent(left, ignored -> new LinkedHashSet<>()).add(new TaskEdge(right, reason));
        edges.computeIfAbsent(right, ignored -> new LinkedHashSet<>()).add(new TaskEdge(left, reason));
    }

    private void connectTaskResource(Map<String, Set<String>> resourcesByTask, Map<String, Set<String>> tasksByResource,
            String taskId, String resourceId)
    {
        resourcesByTask.computeIfAbsent(taskId, ignored -> new TreeSet<>()).add(resourceId);
        tasksByResource.computeIfAbsent(resourceId, ignored -> new TreeSet<>()).add(taskId);
    }

    private void addReason(Map<String, Set<String>> reasons, String entity, String reason)
    {
        reasons.computeIfAbsent(entity, ignored -> new TreeSet<>()).add(reason);
    }

    private record Node(boolean task, String id) { }
    private record TaskEdge(String taskId, String reason) { }

    public record ImpactClosure(List<String> affectedTaskIds, List<String> affectedResourceIds,
            Map<String, List<String>> reasons, boolean globalRevalidationRequired)
    {
        public ImpactClosure
        {
            affectedTaskIds = List.copyOf(affectedTaskIds);
            affectedResourceIds = List.copyOf(affectedResourceIds);
            reasons = Map.copyOf(reasons);
        }
    }
}
