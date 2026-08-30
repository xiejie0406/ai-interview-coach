package com.ruoyi.interview.infrastructure.agent;

import com.ruoyi.interview.application.agent.evidence.EvidenceExtractorPort;
import com.ruoyi.interview.application.agent.port.AgentContentResolverPort;
import com.ruoyi.interview.application.agent.port.AgentInvocationPolicyPort;
import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.application.agent.port.ModelMessage;
import com.ruoyi.interview.application.agent.port.PromptSchemaRegistryPort;
import com.ruoyi.interview.application.agent.port.ProviderRouteRegistryPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.domain.evaluation.EvidenceBundle;
import com.ruoyi.interview.domain.evaluation.EvidenceItem;
import com.ruoyi.interview.domain.evaluation.EvidenceType;
import com.ruoyi.interview.domain.platform.DomainException;
import com.ruoyi.interview.domain.platform.ResourceId;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** evidence-extraction-v1 严格 adapter；offset/quote hash 必须能回指本次固定 AnswerVersion 正文。 */
public final class ChatEvidenceExtractorAdapter implements EvidenceExtractorPort {

    public static final String CAPABILITY = "evaluation.evidence.extract";

    private final StructuredChatExecutor executor;
    private final AgentInputEncoder encoder;
    private final AgentContentResolverPort content;
    private final AgentInvocationPolicyPort invocationPolicy;
    private final IdGeneratorPort ids;

    public ChatEvidenceExtractorAdapter(
            ChatModelPort model,
            PromptSchemaRegistryPort promptSchemas,
            ProviderRouteRegistryPort providerRoutes,
            AgentContentResolverPort content,
            AgentInvocationPolicyPort invocationPolicy,
            IdGeneratorPort ids,
            ObjectMapper objectMapper
    ) {
        this.executor = new StructuredChatExecutor(model, promptSchemas, providerRoutes);
        this.encoder = new AgentInputEncoder(objectMapper);
        this.content = java.util.Objects.requireNonNull(content);
        this.invocationPolicy = java.util.Objects.requireNonNull(invocationPolicy);
        this.ids = java.util.Objects.requireNonNull(ids);
    }

    @Override
    public Result extract(Request request) {
        String answerText = content.resolve(request.tenantId(), request.sourceContentRef(),
                "evaluation-answer");
        if (!sha256(answerText).equalsIgnoreCase(request.answerHash())) {
            throw new IllegalStateException("resolved evaluation answer does not match immutable content hash");
        }
        String input = encoder.encode(Map.of(
                "answerVersionId", request.answerVersionId().value(),
                "answerHash", request.answerHash().toLowerCase(Locale.ROOT),
                "answerText", answerText));
        var invocation = invocationPolicy.authorize(CAPABILITY, request.tenantId(),
                request.evaluationId(), request.correlationId());
        var success = executor.execute(CAPABILITY, request.schemaPin(), request.providerPolicy(),
                List.of(new ModelMessage(ModelMessage.Role.USER, input)),
                Map.of("structuredOutput", "strict"), invocation);
        try {
            StructuredOutputReader root = StructuredOutputReader.root(success.structuredOutput(),
                    Set.of("answerVersionId", "answerHash", "spans"), Set.of());
            if (!root.text("answerVersionId", 1, 128).equals(request.answerVersionId().value())
                    || !root.text("answerHash", 64, 64).equalsIgnoreCase(request.answerHash())) {
                throw new IllegalArgumentException("evidence output source identity does not match request");
            }
            List<EvidenceItem> spans = root.objects("spans", 0, 64,
                            Set.of("evidenceId", "type", "start", "end", "quoteHash"),
                            Set.of("reasonCode"))
                    .stream().map(item -> evidence(item, answerText)).toList();
            EvidenceBundle bundle = new EvidenceBundle(ids.nextResourceId(), request.answerVersionId(),
                    request.answerHash(), request.schemaPin(), spans);
            return new Result(bundle, true, null);
        } catch (IllegalArgumentException | DomainException exception) {
            return new Result(null, false, "EVIDENCE_SCHEMA_INVALID");
        }
    }

    private static EvidenceItem evidence(StructuredOutputReader value, String answerText) {
        int start = value.integer("start", 0, Integer.MAX_VALUE);
        int end = value.integer("end", 1, Integer.MAX_VALUE);
        if (end <= start || end > answerText.length()) {
            throw new IllegalArgumentException("evidence offsets are outside answer text");
        }
        String quoteHash = value.text("quoteHash", 64, 64);
        if (!quoteHash.matches("(?i)[a-f0-9]{64}")
                || !sha256(answerText.substring(start, end)).equalsIgnoreCase(quoteHash)) {
            throw new IllegalArgumentException("evidence quote hash does not match its source span");
        }
        String type = value.text("type", 1, 32);
        return new EvidenceItem(ResourceId.of(value.text("evidenceId", 1, 128)),
                EvidenceType.valueOf(type), start, end, quoteHash,
                value.nullableText("reasonCode", 1, 96));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("required content digest is unavailable", exception);
        }
    }
}

