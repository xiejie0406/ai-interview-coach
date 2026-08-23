package com.ruoyi.interview.infrastructure.agent;

import com.ruoyi.interview.application.agent.port.AgentInvocationPolicyPort;
import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.application.agent.port.ModelMessage;
import com.ruoyi.interview.application.agent.port.PromptSchemaRegistryPort;
import com.ruoyi.interview.application.agent.port.ProviderRouteRegistryPort;
import com.ruoyi.interview.application.agent.report.ReportComposerPort;
import com.ruoyi.interview.domain.evaluation.EvidenceBundle;
import com.ruoyi.interview.domain.evaluation.EvidenceItem;
import com.ruoyi.interview.domain.evaluation.ReportComposition;
import com.ruoyi.interview.domain.evaluation.ReportSection;
import com.ruoyi.interview.domain.evaluation.RubricDimensionScore;
import com.ruoyi.interview.domain.evaluation.RubricScore;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.ResourceId;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** report-composition-v1 严格 adapter；输出只能引用本次 Evidence/Judgement allowlist。 */
public final class ChatReportComposerAdapter implements ReportComposerPort {

    public static final String CAPABILITY = "evaluation.report.compose";

    private final StructuredChatExecutor executor;
    private final AgentInputEncoder encoder;
    private final AgentInvocationPolicyPort invocationPolicy;

    public ChatReportComposerAdapter(
            ChatModelPort model,
            PromptSchemaRegistryPort promptSchemas,
            ProviderRouteRegistryPort providerRoutes,
            AgentInvocationPolicyPort invocationPolicy,
            ObjectMapper objectMapper
    ) {
        this.executor = new StructuredChatExecutor(model, promptSchemas, providerRoutes);
        this.encoder = new AgentInputEncoder(objectMapper);
        this.invocationPolicy = java.util.Objects.requireNonNull(invocationPolicy);
    }

    @Override
    public Result compose(Request request) {
        String input = encoder.encode(Map.of(
                "evaluationId", request.evaluationId().value(),
                "evaluationVersionId", request.evaluationVersionId().value(),
                "evidence", evidence(request.evidenceBundle()),
                "rubricJudgement", judgement(request.rubricJudgement())));
        var invocation = invocationPolicy.authorize(CAPABILITY, request.tenantId(),
                request.evaluationId(), request.correlationId());
        var success = executor.execute(CAPABILITY, request.schemaPin(), request.providerPolicy(),
                List.of(new ModelMessage(ModelMessage.Role.USER, input)),
                Map.of("structuredOutput", "strict"), invocation);
        try {
            StructuredOutputReader root = StructuredOutputReader.root(success.structuredOutput(),
                    Set.of("evaluationVersionId", "sections", "actions", "limitations"), Set.of());
            if (!root.text("evaluationVersionId", 1, 128)
                    .equals(request.evaluationVersionId().value())) {
                throw new IllegalArgumentException("report output references another evaluation version");
            }
            Set<String> evidenceIds = request.evidenceBundle().spans().stream()
                    .map(item -> item.evidenceId().value()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> dimensionIds = request.rubricJudgement().dimensions().stream()
                    .map(RubricDimensionScore::dimensionId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<ReportSection> sections = root.objects("sections", 0, 24,
                            Set.of("sectionId", "title", "body", "judgementRefs", "evidenceRefs"), Set.of())
                    .stream().map(section -> section(section, evidenceIds, dimensionIds)).toList();
            ReportComposition composition = new ReportComposition(request.evaluationVersionId(), sections,
                    root.strings("actions", 0, 3, 500, false),
                    root.strings("limitations", 0, 16, 500, false));
            return new Result(composition, true, null);
        } catch (IllegalArgumentException | DomainException exception) {
            return new Result(null, false, "REPORT_SCHEMA_INVALID");
        }
    }

    private static ReportSection section(
            StructuredOutputReader value,
            Set<String> evidenceIds,
            Set<String> dimensionIds
    ) {
        List<String> judgementRefs = value.strings("judgementRefs", 0, 64, 128, true);
        List<String> evidenceRefs = value.strings("evidenceRefs", 0, 64, 128, true);
        if (!dimensionIds.containsAll(judgementRefs) || !evidenceIds.containsAll(evidenceRefs)) {
            throw new IllegalArgumentException("report output contains a foreign evidence/judgement reference");
        }
        return new ReportSection(value.text("sectionId", 1, 128),
                value.text("title", 1, 200), value.text("body", 1, 8_000), judgementRefs,
                evidenceRefs.stream().map(ResourceId::of).toList());
    }

    private static Map<String, Object> evidence(EvidenceBundle bundle) {
        return Map.of(
                "bundleId", bundle.bundleId().value(),
                "answerVersionId", bundle.answerVersionId().value(),
                "answerHash", bundle.answerHash(),
                "spans", bundle.spans().stream().map(ChatReportComposerAdapter::evidenceItem).toList());
    }

    private static Map<String, Object> evidenceItem(EvidenceItem item) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("evidenceId", item.evidenceId().value());
        value.put("type", item.type().name());
        value.put("start", item.startInclusive());
        value.put("end", item.endExclusive());
        value.put("quoteHash", item.quoteHash());
        item.reasonCode().ifPresent(reason -> value.put("reasonCode", reason));
        return Map.copyOf(value);
    }

    private static Map<String, Object> judgement(RubricScore score) {
        return Map.of(
                "judgementId", score.judgementId().value(),
                "rubricVersionId", score.rubricVersionId(),
                "dimensions", score.dimensions().stream()
                        .map(ChatReportComposerAdapter::dimension).toList(),
                "limitations", score.limitations());
    }

    private static Map<String, Object> dimension(RubricDimensionScore value) {
        return Map.of(
                "dimensionId", value.dimensionId(),
                "judgement", value.judgement().name(),
                "confidence", value.confidence().name(),
                "insufficientEvidence", value.insufficientEvidence(),
                "reasonCodes", value.reasonCodes(),
                "evidenceIds", value.evidenceIds().stream().map(ResourceId::value).toList());
    }
}

