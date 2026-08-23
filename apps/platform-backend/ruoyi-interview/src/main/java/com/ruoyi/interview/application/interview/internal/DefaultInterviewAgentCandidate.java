package com.ruoyi.interview.application.interview.internal;

import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.application.agent.port.ChatModelRequest;
import com.ruoyi.interview.application.agent.port.ChatModelResult;
import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.ModelMessage;
import com.ruoyi.interview.application.interview.InterviewAgentCandidate;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.domain.platform.CostBudget;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import com.ruoyi.interview.domain.platform.Money;
import com.ruoyi.interview.domain.platform.PromptRef;
import com.ruoyi.interview.domain.platform.ProviderConfigRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.SchemaRef;
import com.ruoyi.interview.domain.platform.TimeBudget;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 结构化 Interview Agent；坏输出或 Provider 失败安全降级为 NEXT。 */
public final class DefaultInterviewAgentCandidate implements InterviewAgentCandidate {
    private static final PromptRef PROMPT = new PromptRef(new ImmutableVersionRef(
            ResourceId.of("interview-agent-prompt-v1"), 1, "interview-agent-prompt-v1"));
    private static final SchemaRef SCHEMA = new SchemaRef(new ImmutableVersionRef(
            ResourceId.of("interview-agent-schema-v1"), 1, "interview-agent-schema-v1"));
    private final ChatModelPort model;
    private final IdGeneratorPort ids;
    private final String modelAlias;

    public DefaultInterviewAgentCandidate(ChatModelPort model, IdGeneratorPort ids, String modelAlias) {
        this.model = java.util.Objects.requireNonNull(model);
        this.ids = java.util.Objects.requireNonNull(ids);
        this.modelAlias = java.util.Objects.requireNonNull(modelAlias);
    }

    @Override
    public Result propose(Query query) {
        if (query.remainingFollowUpBudget() <= 0) return next(Optional.empty());
        var request = new ChatModelRequest(PROMPT, SCHEMA,
                new ProviderConfigRef("INTERVIEW_AGENT", "deepseek", modelAlias, 1),
                List.of(
                        new ModelMessage(ModelMessage.Role.SYSTEM,
                                "你是技术面试官。只输出 JSON：{\"action\":\"FOLLOW_UP|NEXT\",\"questionText\":\"...\"}。只有回答存在值得澄清的技术点时追问；一次只问一个问题，不评分，不泄露参考答案。"),
                        new ModelMessage(ModelMessage.Role.USER,
                                "当前问题：" + query.questionText() + "\n候选人已确认回答：" + query.confirmedAnswer()
                                        + "\n剩余追问预算：" + query.remainingFollowUpBudget())),
                Map.of("temperature", "0.2", "maxTokens", "500"));
        var invocation = new InvocationContext(query.tenantId(), ids.nextResourceId(),
                query.context().correlationId(), new TimeBudget(Duration.ofSeconds(30)),
                new CostBudget(new Money(BigDecimal.ONE, "CNY")));
        ChatModelResult response = model.execute(request, invocation);
        if (response instanceof ChatModelResult.Failure failure) {
            return next(Optional.of(failure.failure().errorClass()));
        }
        Map<String, Object> output = ((ChatModelResult.Success) response).structuredOutput();
        if (!"FOLLOW_UP".equals(output.get("action"))) return next(Optional.empty());
        Object textValue = output.get("questionText");
        if (!(textValue instanceof String text)) return next(Optional.of("AGENT_BAD_OUTPUT"));
        String normalized = text.trim();
        if (normalized.isEmpty() || normalized.length() > 1000 || normalized.contains("\n\n")) {
            return next(Optional.of("AGENT_BAD_OUTPUT"));
        }
        return new Result(Result.Action.FOLLOW_UP, Optional.of(normalized), Optional.empty());
    }

    private static Result next(Optional<String> failure) {
        return new Result(Result.Action.NEXT, Optional.empty(), failure);
    }
}
