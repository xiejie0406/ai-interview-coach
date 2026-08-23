package com.aiinterviewcoach.adapters.outbound.persistence.platform;

import com.aiinterviewcoach.application.platform.port.DurableStreamPort;
import com.aiinterviewcoach.domain.platform.DomainEvent;
import com.aiinterviewcoach.domain.platform.DurableStreamType;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interview/Evaluation durable 事件注册表候选。Retention 数值必须由 Boot 的已批准配置传入；
 * 本类不硬编码 replay window，也不把原始 attributes 整体复制到公开事件。
 */
public final class StrictDurableStreamEventPolicy implements DurableStreamEventPolicy {

    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> NON_DURABLE_INTERVIEW_EVENTS = Set.of(
            "interview.plan.created",
            "interview.plan.revised",
            "interview.plan.confirmed",
            "interview.plan.expired",
            "interview.plan.cancelled",
            "interview.turn.planned"
    );
    private static final Set<String> NON_DURABLE_EVALUATION_EVENTS = Set.of(
            "evaluation.report.feedback_appended"
    );

    private final Duration interviewRetention;
    private final Duration evaluationRetention;

    public StrictDurableStreamEventPolicy(Duration interviewRetention, Duration evaluationRetention) {
        this.interviewRetention = requirePositive(interviewRetention, "interviewStreamRetention");
        this.evaluationRetention = requirePositive(evaluationRetention, "evaluationStreamRetention");
    }

    @Override
    public Optional<DurableStreamPort.AppendCommand> classify(DomainEvent event) {
        java.util.Objects.requireNonNull(event, "domainEvent");
        String type = event.eventType();
        if (NON_DURABLE_INTERVIEW_EVENTS.contains(type)
                || NON_DURABLE_EVALUATION_EVENTS.contains(type)) {
            return Optional.empty();
        }
        if (type.startsWith("interview.")) {
            return Optional.of(interview(event));
        }
        if (type.startsWith("evaluation.")) {
            return Optional.of(evaluation(event));
        }
        // 其他逻辑域当前没有批准的用户 SSE channel，因此是显式 namespace-level skip。
        return Optional.empty();
    }

    private DurableStreamPort.AppendCommand interview(DomainEvent event) {
        String publicType = "interview.session.state";
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        switch (event.eventType()) {
            case "interview.session.ready" -> {
                data.putAll(allowed(event, Set.of("planId", "mode"), Set.of()));
                data.put("state", "READY");
            }
            case "interview.session.started", "interview.session.resumed", "interview.session.recovered" -> {
                requireNoAttributes(event);
                data.put("state", "IN_PROGRESS");
            }
            case "interview.question.committed" -> {
                publicType = "interview.question.committed";
                Map<String, String> attributes = allowed(event, Set.of("turnId", "sequence"), Set.of());
                data.put("turnId", attributes.get("turnId"));
                data.put("turnSequence", attributes.get("sequence"));
            }
            case "interview.turn.answer_confirmed" -> {
                Map<String, String> attributes = allowed(event,
                        Set.of("turnId", "answerVersionId", "sequence"), Set.of());
                data.put("state", "IN_PROGRESS");
                data.put("turnId", attributes.get("turnId"));
                data.put("turnSequence", attributes.get("sequence"));
                data.put("answerVersionId", attributes.get("answerVersionId"));
            }
            case "interview.turn.skipped" -> {
                Map<String, String> attributes = allowed(event, Set.of("turnId", "sequence"), Set.of());
                data.put("state", "IN_PROGRESS");
                data.put("turnId", attributes.get("turnId"));
                data.put("turnSequence", attributes.get("sequence"));
            }
            case "interview.session.paused" -> {
                requireNoAttributes(event);
                data.put("state", "PAUSED");
            }
            case "interview.session.recoverable_failure" -> {
                data.putAll(allowed(event, Set.of("failureCode"), Set.of()));
                data.put("state", "FAILED_RECOVERABLE");
            }
            case "interview.session.failed_final" -> {
                data.putAll(allowed(event, Set.of("failureCode"), Set.of()));
                data.put("state", "FAILED_FINAL");
            }
            case "interview.session.completing" -> {
                requireNoAttributes(event);
                data.put("state", "COMPLETING");
            }
            case "interview.session.completed" -> {
                requireNoAttributes(event);
                data.put("state", "COMPLETED");
            }
            case "interview.session.cancelled" -> {
                requireNoAttributes(event);
                data.put("state", "CANCELLED");
            }
            default -> throw unregistered(event);
        }
        data.put("aggregateVersion", Long.toString(event.aggregateVersion().value()));
        return append(event, DurableStreamType.INTERVIEW, event.aggregateId(), publicType,
                data, interviewRetention);
    }

    private DurableStreamPort.AppendCommand evaluation(DomainEvent event) {
        String publicType = "evaluation.state.changed";
        ResourceId streamId = event.aggregateId();
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        switch (event.eventType()) {
            case "evaluation.requested" -> {
                data.putAll(allowed(event,
                        Set.of("answerVersionId", "sourceInterviewId", "configVersionId"), Set.of()));
                data.put("state", "PENDING");
                data.put("stage", "QUEUED");
            }
            case "evaluation.evidence.extracting" -> {
                requireNoAttributes(event);
                data.put("state", "RUNNING");
                data.put("stage", "EVIDENCE_EXTRACTING");
            }
            case "evaluation.evidence.attached" -> {
                data.putAll(allowed(event, Set.of("evidenceBundleId"), Set.of()));
                data.put("state", "RUNNING");
                data.put("stage", "RUBRIC_JUDGING");
            }
            case "evaluation.rubric.attached" -> {
                data.putAll(allowed(event, Set.of("judgementId", "evaluationVersionId"), Set.of()));
                data.put("state", "RUNNING");
                data.put("stage", "REPORT_COMPOSING");
            }
            case "evaluation.succeeded" -> {
                data.putAll(allowed(event, Set.of("reportId"), Set.of()));
                data.put("state", "SUCCEEDED");
                data.put("stage", "COMPLETE");
            }
            case "evaluation.manual_review_required" -> {
                data.putAll(allowed(event, Set.of("reasonCode"), Set.of()));
                data.put("state", "RUNNING");
                data.put("stage", "MANUAL_REVIEW");
            }
            case "evaluation.failed_retryable" -> {
                data.putAll(allowed(event, Set.of("failureCode"), Set.of()));
                data.put("state", "FAILED_RETRYABLE");
            }
            case "evaluation.failed_final" -> {
                data.putAll(allowed(event, Set.of("failureCode"), Set.of()));
                data.put("state", "FAILED_FINAL");
            }
            case "evaluation.retry_started" -> {
                data.putAll(allowed(event, Set.of("stage"), Set.of()));
                data.put("state", "RUNNING");
            }
            case "evaluation.cancelled" -> {
                data.putAll(allowed(event, Set.of("stage"), Set.of()));
                data.put("state", "CANCELLED");
            }
            case "evaluation.report.published" -> {
                publicType = "evaluation.report.ready";
                Map<String, String> attributes = allowed(event,
                        Set.of("evaluationId", "reportVersionId", "state"), Set.of());
                streamId = ResourceId.of(attributes.get("evaluationId"));
                data.put("reportId", event.aggregateId().value());
                data.put("reportVersionId", attributes.get("reportVersionId"));
                data.put("state", attributes.get("state"));
            }
            default -> throw unregistered(event);
        }
        data.put("aggregateVersion", Long.toString(event.aggregateVersion().value()));
        return append(event, DurableStreamType.EVALUATION, streamId, publicType,
                data, evaluationRetention);
    }

    private static DurableStreamPort.AppendCommand append(
            DomainEvent event,
            DurableStreamType streamType,
            ResourceId streamId,
            String publicType,
            Map<String, String> data,
            Duration retention
    ) {
        return new DurableStreamPort.AppendCommand(event.tenantId(), streamType, streamId,
                ResourceId.of(event.eventId()), event.aggregateId(), event.aggregateVersion(), publicType,
                event.occurredAt(), SCHEMA_VERSION, event.correlationId(), data,
                event.occurredAt().plus(retention));
    }

    private static Map<String, String> allowed(
            DomainEvent event,
            Set<String> required,
            Set<String> optional
    ) {
        Set<String> allowed = new java.util.HashSet<>(required);
        allowed.addAll(optional);
        if (!allowed.containsAll(event.attributes().keySet())) {
            throw new IllegalStateException("durable stream event contains non-allowlisted attributes: "
                    + event.eventType());
        }
        if (!event.attributes().keySet().containsAll(required)) {
            throw new IllegalStateException("durable stream event is missing required attributes: "
                    + event.eventType());
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        allowed.stream().sorted().forEach(key -> {
            String value = event.attributes().get(key);
            if (value != null) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }

    private static void requireNoAttributes(DomainEvent event) {
        if (!event.attributes().isEmpty()) {
            throw new IllegalStateException("durable stream event unexpectedly contains attributes: "
                    + event.eventType());
        }
    }

    private static IllegalStateException unregistered(DomainEvent event) {
        return new IllegalStateException("durable stream event type is not registered: " + event.eventType());
    }

    private static Duration requirePositive(Duration value, String name) {
        java.util.Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
