package com.aiinterviewcoach.adapters.outbound.agent;

import com.aiinterviewcoach.application.agent.learning.LearningCoachPort;
import com.aiinterviewcoach.application.agent.port.AgentContentResolverPort;
import com.aiinterviewcoach.application.agent.port.AgentInvocationPolicyPort;
import com.aiinterviewcoach.application.agent.port.ChatModelPort;
import com.aiinterviewcoach.application.agent.port.ModelMessage;
import com.aiinterviewcoach.application.agent.port.PromptSchemaRegistryPort;
import com.aiinterviewcoach.application.agent.port.ProviderRouteRegistryPort;
import com.aiinterviewcoach.domain.platform.DomainException;
import com.aiinterviewcoach.domain.platform.ResourceId;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** learning-plan-v1 严格 adapter；只允许选择调用方提供的 QuestionVersion allowlist。 */
public final class ChatLearningCoachAdapter implements LearningCoachPort {

    public static final String CAPABILITY = "learning.coach";

    private final StructuredChatExecutor executor;
    private final AgentInputEncoder encoder;
    private final AgentContentResolverPort content;
    private final AgentInvocationPolicyPort invocationPolicy;

    public ChatLearningCoachAdapter(
            ChatModelPort model,
            PromptSchemaRegistryPort promptSchemas,
            ProviderRouteRegistryPort providerRoutes,
            AgentContentResolverPort content,
            AgentInvocationPolicyPort invocationPolicy,
            ObjectMapper objectMapper
    ) {
        this.executor = new StructuredChatExecutor(model, promptSchemas, providerRoutes);
        this.encoder = new AgentInputEncoder(objectMapper);
        this.content = java.util.Objects.requireNonNull(content);
        this.invocationPolicy = java.util.Objects.requireNonNull(invocationPolicy);
    }

    @Override
    public Result recommend(Request request) {
        String reportText = content.resolve(request.tenantId(), request.report().contentRef(),
                "learning-source-report");
        List<Map<String, Object>> questionInputs = request.allowedQuestions().stream()
                .map(question -> questionInput(request, question)).toList();
        String input = encoder.encode(Map.of(
                "sourceReportId", request.report().reportId().value(),
                "sourceReportVersionId", request.report().reportVersionId().value(),
                "report", reportText,
                "allowedQuestions", questionInputs));
        var invocation = invocationPolicy.authorize(CAPABILITY, request.tenantId(),
                request.report().reportId(), request.correlationId());
        var success = executor.execute(CAPABILITY, request.schemaPin(), request.providerPolicy(),
                List.of(new ModelMessage(ModelMessage.Role.USER, input)),
                Map.of("structuredOutput", "strict", "configVersionId", request.configVersionId()),
                invocation);
        try {
            StructuredOutputReader root = StructuredOutputReader.root(success.structuredOutput(),
                    Set.of("sourceReportVersionId", "items", "limitations"), Set.of());
            if (!root.text("sourceReportVersionId", 1, 128)
                    .equals(request.report().reportVersionId().value())) {
                throw new IllegalArgumentException("learning output references another report version");
            }
            Set<ResourceId> allowed = request.allowedQuestions().stream()
                    .map(AllowedQuestionVersion::questionVersionId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<CandidateItem> items = root.objects("items", 0, 12,
                            Set.of("itemId", "questionVersionId", "weaknessRef", "reasonCodes", "priority"),
                            Set.of("suggestedDueAt"))
                    .stream().map(item -> item(item, allowed)).toList();
            if (new HashSet<>(items.stream().map(CandidateItem::candidateItemId).toList()).size()
                    != items.size()) {
                throw new IllegalArgumentException("learning output item ids are not unique");
            }
            Candidate candidate = new Candidate(request.report().reportVersionId(), items,
                    root.strings("limitations", 0, 16, 500, false));
            return new Result(candidate, true, null);
        } catch (IllegalArgumentException | DomainException exception) {
            return new Result(null, false, "LEARNING_SCHEMA_INVALID");
        }
    }

    private Map<String, Object> questionInput(Request request, AllowedQuestionVersion question) {
        return Map.of(
                "questionVersionId", question.questionVersionId().value(),
                "title", question.title(),
                "content", content.resolve(request.tenantId(), question.contentRef(),
                        "learning-allowed-question"));
    }

    private static CandidateItem item(StructuredOutputReader value, Set<ResourceId> allowed) {
        ResourceId questionId = ResourceId.of(value.text("questionVersionId", 1, 128));
        if (!allowed.contains(questionId)) {
            throw new IllegalArgumentException("learning output selected a question outside allowlist");
        }
        return new CandidateItem(value.text("itemId", 1, 128), questionId,
                value.text("weaknessRef", 1, 128),
                value.strings("reasonCodes", 0, 8, 96, true),
                value.integer("priority", 1, 5), value.nullableInstant("suggestedDueAt"));
    }
}
