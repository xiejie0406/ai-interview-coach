package com.ruoyi.aps.domain.routing;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGraphValidatorTest
{
    private final RouteGraphValidator validator = new RouteGraphValidator();

    @Test
    void validBranchAndMergeIsADag()
    {
        var nodes = List.of(node("A"), node("B"), node("C"), node("D"));
        var edges = List.of(edge("1", "A", "B"), edge("2", "A", "C"), edge("3", "B", "D"), edge("4", "C", "D"));

        assertThat(validator.validate(new RouteGraphValidator.Graph("route", nodes, edges), true)).isEmpty();
    }

    @Test
    void cycleAndSelfDependencyAreObjectLevelProblems()
    {
        var graph = new RouteGraphValidator.Graph("route", List.of(node("A"), node("B")),
                List.of(edge("1", "A", "B"), edge("2", "B", "A"), edge("3", "A", "A")));

        assertThat(validator.validate(graph, false)).extracting(RouteGraphValidator.Issue::code)
                .contains("ROUTE_CYCLE", "SELF_DEPENDENCY");
    }

    @Test
    void sameStartIsRetainedInDraftButBlocksPublish()
    {
        var sync = new RouteGraphValidator.Edge("sync", "A", "B", DependencyType.SAME_START,
                null, null, null, 0, false);
        var graph = new RouteGraphValidator.Graph("route", List.of(node("A"), node("B")), List.of(sync));

        assertThat(validator.validate(graph, false)).isEmpty();
        assertThat(validator.validate(graph, true)).extracting(RouteGraphValidator.Issue::code)
                .containsExactly("UNSUPPORTED_SYNC_RULE");
    }

    @Test
    void invalidQuantityRuleAndMissingReadinessAreReported()
    {
        var bad = new RouteGraphValidator.Node("A", "A", false, false, false);
        var edge = new RouteGraphValidator.Edge("q", "A", "B", DependencyType.QUANTITY,
                new BigDecimal("2"), new BigDecimal("0.5"), null, 0, false);
        var graph = new RouteGraphValidator.Graph("route", List.of(bad, node("B")), List.of(edge));

        assertThat(validator.validate(graph, true)).extracting(RouteGraphValidator.Issue::code)
                .contains("MISSING_DURATION", "NO_RESOURCE_REQUIREMENT", "UNKNOWN_RESOURCE_REQUIREMENT",
                        "INVALID_QUANTITY_THRESHOLD");
    }

    @Test
    void disconnectedSubgraphsBlockPublish()
    {
        var graph = new RouteGraphValidator.Graph("route", List.of(node("A"), node("B"), node("C"), node("D")),
                List.of(edge("1", "A", "B"), edge("2", "C", "D")));

        assertThat(validator.validate(graph, true)).extracting(RouteGraphValidator.Issue::code)
                .contains("DISCONNECTED_ROUTE");
    }

    private RouteGraphValidator.Node node(String id)
    {
        return new RouteGraphValidator.Node(id, id, true, true, true);
    }

    private RouteGraphValidator.Edge edge(String id, String from, String to)
    {
        return new RouteGraphValidator.Edge(id, from, to, DependencyType.FINISH, null, null, null, 0, false);
    }
}
