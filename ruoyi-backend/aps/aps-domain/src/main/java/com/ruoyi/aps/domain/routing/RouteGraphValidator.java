package com.ruoyi.aps.domain.routing;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 不依赖数据库和求解器的路线 DAG 校验器。
 *
 * <p>草稿保存可以保留不完整工艺，但结构错误（重复、自依赖、悬空边、环）始终阻断；
 * 发布时再检查工时、资源要求、孤立节点和 P0 不支持的同步规则。</p>
 */
public final class RouteGraphValidator
{
    public List<Issue> validate(Graph graph, boolean publish)
    {
        List<Issue> issues = new ArrayList<>();
        Map<String, Node> nodes = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (Node node : graph.nodes())
        {
            if (nodes.putIfAbsent(node.id(), node) != null)
            {
                issues.add(issue("DUPLICATE_NODE", "ROUTE_NODE", node.id(), "id", "路线节点 ID 重复"));
            }
            if (!codes.add(node.code()))
            {
                issues.add(issue("DUPLICATE_NODE_CODE", "ROUTE_NODE", node.id(), "code", "路线节点编码重复"));
            }
            if (publish && !node.hasDuration())
            {
                issues.add(issue("MISSING_DURATION", "ROUTE_NODE", node.id(), "operationSpecId", "工序没有可复算的阶段工时"));
            }
            if (publish && !node.hasResourceRequirement())
            {
                issues.add(issue("NO_RESOURCE_REQUIREMENT", "ROUTE_NODE", node.id(), "operationSpecId", "工序没有必需资源要求"));
            }
            if (publish && !node.resourceReferencesKnown())
            {
                issues.add(issue("UNKNOWN_RESOURCE_REQUIREMENT", "ROUTE_NODE", node.id(), "operationSpecId", "工序引用了不存在或类型不符的资源要求"));
            }
        }
        if (graph.nodes().isEmpty())
        {
            issues.add(issue("NO_ROUTE_NODE", "ROUTE_VERSION", graph.routeVersionId(), "nodes", "路线至少需要一个节点"));
            return List.copyOf(issues);
        }

        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Set<String>> undirected = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Integer> degree = new HashMap<>();
        nodes.keySet().forEach(id -> {
            outgoing.put(id, new HashSet<>());
            undirected.put(id, new HashSet<>());
            indegree.put(id, 0);
            degree.put(id, 0);
        });
        Set<String> pairs = new HashSet<>();
        for (Edge edge : graph.edges())
        {
            Node predecessor = nodes.get(edge.predecessorNodeId());
            Node successor = nodes.get(edge.successorNodeId());
            if (predecessor == null || successor == null)
            {
                issues.add(issue("MISSING_EDGE_NODE", "ROUTE_EDGE", edge.id(), "nodeId", "依赖引用了不在本路线中的节点"));
                continue;
            }
            if (predecessor.id().equals(successor.id()))
            {
                issues.add(issue("SELF_DEPENDENCY", "ROUTE_EDGE", edge.id(), "successorNodeId", "路线节点不能依赖自身"));
                continue;
            }
            String pair = predecessor.id() + "\u0000" + successor.id();
            if (!pairs.add(pair))
            {
                issues.add(issue("DUPLICATE_EDGE", "ROUTE_EDGE", edge.id(), null, "两个节点间的依赖重复"));
                continue;
            }
            validateEdge(edge, publish, issues);
            outgoing.get(predecessor.id()).add(successor.id());
            undirected.get(predecessor.id()).add(successor.id());
            undirected.get(successor.id()).add(predecessor.id());
            indegree.compute(successor.id(), (key, value) -> value + 1);
            degree.compute(predecessor.id(), (key, value) -> value + 1);
            degree.compute(successor.id(), (key, value) -> value + 1);
        }

        if (publish && nodes.size() > 1)
        {
            degree.forEach((id, value) -> {
                if (value == 0)
                {
                    issues.add(issue("ISOLATED_NODE", "ROUTE_NODE", id, null, "多节点路线中存在孤立节点"));
                }
            });
            Set<String> connected = new HashSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>();
            pending.add(nodes.keySet().iterator().next());
            while (!pending.isEmpty())
            {
                String id = pending.removeFirst();
                if (connected.add(id)) pending.addAll(undirected.get(id));
            }
            if (connected.size() != nodes.size())
            {
                issues.add(issue("DISCONNECTED_ROUTE", "ROUTE_VERSION", graph.routeVersionId(), "edges", "路线节点未形成同一个可追溯网络"));
            }
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, value) -> { if (value == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty())
        {
            String id = queue.removeFirst();
            visited++;
            for (String next : outgoing.get(id))
            {
                int nextDegree = indegree.compute(next, (key, value) -> value - 1);
                if (nextDegree == 0) queue.add(next);
            }
        }
        if (visited != nodes.size())
        {
            issues.add(issue("ROUTE_CYCLE", "ROUTE_VERSION", graph.routeVersionId(), "edges", "路线依赖形成了循环"));
        }
        return List.copyOf(issues);
    }

    private void validateEdge(Edge edge, boolean publish, List<Issue> issues)
    {
        if (edge.type() == DependencyType.FINISH
                && (edge.thresholdQty() != null || edge.thresholdRatio() != null || edge.transferBatchQty() != null))
        {
            issues.add(issue("INVALID_FINISH_RULE", "ROUTE_EDGE", edge.id(), null, "完成依赖不能设置数量门槛或转移批"));
        }
        if (edge.type() == DependencyType.QUANTITY)
        {
            boolean qty = positive(edge.thresholdQty());
            boolean ratio = positive(edge.thresholdRatio()) && edge.thresholdRatio().compareTo(BigDecimal.ONE) <= 0;
            if (qty == ratio)
            {
                issues.add(issue("INVALID_QUANTITY_THRESHOLD", "ROUTE_EDGE", edge.id(), "threshold", "数量依赖必须且只能设置有效数量或比例门槛"));
            }
            if (edge.transferBatchQty() != null && !positive(edge.transferBatchQty()))
            {
                issues.add(issue("INVALID_TRANSFER_BATCH", "ROUTE_EDGE", edge.id(), "transferBatchQty", "转移批必须大于零"));
            }
        }
        if (edge.type() == DependencyType.SAME_START)
        {
            boolean carriesUnsupportedFields = edge.thresholdQty() != null || edge.thresholdRatio() != null
                    || edge.transferBatchQty() != null || edge.lagSeconds() != 0 || edge.consumesOutput();
            if (carriesUnsupportedFields)
            {
                issues.add(issue("INVALID_SYNC_RULE", "ROUTE_EDGE", edge.id(), null, "SAME_START 不能携带门槛、转移批、消耗或 lag"));
            }
            if (publish)
            {
                issues.add(issue("UNSUPPORTED_SYNC_RULE", "ROUTE_EDGE", edge.id(), "dependencyType", "P0 保留 SAME_START 来源，但不允许发布执行"));
            }
        }
    }

    private boolean positive(BigDecimal value)
    {
        return value != null && value.signum() > 0;
    }

    private Issue issue(String code, String objectType, String objectId, String field, String message)
    {
        return new Issue(code, objectType, objectId, field, message);
    }

    public record Graph(String routeVersionId, List<Node> nodes, List<Edge> edges)
    {
        public Graph
        {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    public record Node(String id, String code, boolean hasDuration, boolean hasResourceRequirement,
            boolean resourceReferencesKnown)
    {
    }

    public record Edge(String id, String predecessorNodeId, String successorNodeId, DependencyType type,
            BigDecimal thresholdQty, BigDecimal thresholdRatio, BigDecimal transferBatchQty, int lagSeconds,
            boolean consumesOutput)
    {
    }

    public record Issue(String code, String objectType, String objectId, String field, String message)
    {
    }
}
