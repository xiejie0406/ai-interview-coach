package com.aiinterviewcoach.adapters.outbound.persistence.platform;

import com.aiinterviewcoach.domain.platform.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Conservative event envelope registry for the outbox.
 *
 * <p>Only registered domain event namespaces are accepted. Payload references are restricted to stable identifiers,
 * versions and reason codes; free-form text, transcript, prompt and profile content must stay in encrypted aggregate
 * tables and never be copied into the outbox envelope.</p>
 */
public final class StrictDomainEventEnvelopePolicy implements DomainEventEnvelopePolicy {

    private static final int SCHEMA_VERSION = 1;
    private static final Map<String, Rule> RULES = rules();

    @Override
    public Envelope classify(DomainEvent event) {
        java.util.Objects.requireNonNull(event, "domainEvent");
        Rule rule = RULES.get(event.eventType());
        if (rule == null) {
            throw new IllegalStateException("domain event type is not registered: " + event.eventType());
        }
        return new Envelope(rule.aggregateType(), SCHEMA_VERSION, safePayloadReferences(event, rule));
    }

    private static Map<String, String> safePayloadReferences(DomainEvent event, Rule rule) {
        Set<String> actual = event.attributes().keySet();
        if (!actual.containsAll(rule.requiredKeys())) {
            throw new IllegalStateException("domain event is missing required outbox references: "
                    + event.eventType());
        }
        Set<String> allowed = new java.util.HashSet<>(rule.requiredKeys());
        allowed.addAll(rule.optionalKeys());
        if (!allowed.containsAll(actual)) {
            throw new IllegalStateException("domain event contains non-allowlisted outbox references: "
                    + event.eventType());
        }
        LinkedHashMap<String, String> references = new LinkedHashMap<>();
        event.attributes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(attribute -> {
            String key = attribute.getKey();
            String value = attribute.getValue();
            if (value == null || value.isBlank() || value.length() > 256) {
                throw new IllegalStateException("domain event safe reference is blank or too long: "
                        + event.eventType() + "/" + key);
            }
            if ((key.endsWith("Code") || key.equals("failureCode") || key.equals("errorCode"))
                    && !value.matches("[A-Z][A-Z0-9_]{0,127}")) {
                throw new IllegalStateException("domain event reason/failure code is malformed: "
                        + event.eventType() + "/" + key);
            }
            if (rule.publishedKeys().contains(key)) {
                references.put(key, value);
            }
        });
        return Map.copyOf(references);
    }

    private static Map<String, Rule> rules() {
        LinkedHashMap<String, Rule> rules = new LinkedHashMap<>();

        register(rules, "IDENTITY_USER_ACCOUNT", Set.of("userId"), Set.of(),
                "identity.user.registered");
        register(rules, "IDENTITY_USER_ACCOUNT", Set.of(), Set.of(),
                "identity.user.profile_changed");
        register(rules, "IDENTITY_USER_ACCOUNT", Set.of("status"), Set.of(),
                "identity.user.activated", "identity.user.locked", "identity.user.disabled",
                "identity.user.closing_started", "identity.user.closed");
        register(rules, "IDENTITY_TENANT", Set.of("tenantType"), Set.of("ownerUserId"),
                "identity.tenant.created");
        register(rules, "IDENTITY_TENANT", Set.of("status"), Set.of(),
                "identity.tenant.suspended", "identity.tenant.reactivated",
                "identity.tenant.closing_started", "identity.tenant.closed");
        register(rules, "IDENTITY_MEMBERSHIP", Set.of("userId", "role"), Set.of(),
                "identity.membership.joined");
        register(rules, "IDENTITY_MEMBERSHIP", Set.of("status"), Set.of(),
                "identity.membership.suspended", "identity.membership.reactivated",
                "identity.membership.left");

        register(rules, "CATALOG_QUESTION", Set.of("stableKey"), Set.of(), Set.of(),
                "catalog.question.created");
        register(rules, "CATALOG_QUESTION", Set.of("versionNo"), Set.of(),
                "catalog.question.draft_attached");
        register(rules, "CATALOG_QUESTION", Set.of(), Set.of(),
                "catalog.question.submitted_for_review", "catalog.question.retired");
        register(rules, "CATALOG_QUESTION", Set.of("reasonCode", "reviewedBy"), Set.of(),
                "catalog.question.review_rejected");
        register(rules, "CATALOG_QUESTION",
                Set.of("questionVersionId", "rubricVersionId", "publicationId", "reasonCode", "reviewedBy"),
                Set.of(), "catalog.question.published");

        register(rules, "PRACTICE_ATTEMPT", Set.of("questionVersionId"), Set.of(),
                "practice.attempt.started");
        register(rules, "PRACTICE_ATTEMPT", Set.of(), Set.of(),
                "practice.draft.saved", "practice.attempt.cancelled");
        register(rules, "PRACTICE_ATTEMPT", Set.of("answerVersionId", "answerVersionNo"), Set.of(),
                "practice.answer.submitted");

        register(rules, "INTERVIEW_PLAN", Set.of("mode", "planVersionNo"), Set.of(),
                "interview.plan.created");
        register(rules, "INTERVIEW_PLAN", Set.of("planVersionNo"), Set.of(),
                "interview.plan.revised");
        register(rules, "INTERVIEW_PLAN", Set.of("planVersionNo", "usageReservationId"), Set.of(),
                "interview.plan.confirmed");
        register(rules, "INTERVIEW_PLAN", Set.of(), Set.of(),
                "interview.plan.cancelled", "interview.plan.expired");
        register(rules, "INTERVIEW_SESSION", Set.of("planId", "mode"), Set.of(),
                "interview.session.ready");
        register(rules, "INTERVIEW_SESSION", Set.of(), Set.of(),
                "interview.session.started", "interview.session.paused", "interview.session.resumed",
                "interview.session.recovered", "interview.session.completing", "interview.session.completed",
                "interview.session.cancelled");
        register(rules, "INTERVIEW_SESSION", Set.of("turnId", "sequence"), Set.of(),
                "interview.turn.planned", "interview.question.committed", "interview.turn.skipped");
        register(rules, "INTERVIEW_SESSION", Set.of("turnId", "answerVersionId", "sequence"), Set.of(),
                "interview.turn.answer_confirmed");
        register(rules, "INTERVIEW_SESSION", Set.of("failureCode"), Set.of(),
                "interview.session.recoverable_failure", "interview.session.failed_final");

        register(rules, "EVALUATION_RUN",
                Set.of("answerVersionId", "sourceInterviewId", "configVersionId"), Set.of(),
                "evaluation.requested");
        register(rules, "EVALUATION_RUN", Set.of(), Set.of(), "evaluation.evidence.extracting");
        register(rules, "EVALUATION_RUN", Set.of("evidenceBundleId"), Set.of(),
                "evaluation.evidence.attached");
        register(rules, "EVALUATION_RUN", Set.of("judgementId", "evaluationVersionId"), Set.of(),
                "evaluation.rubric.attached");
        register(rules, "EVALUATION_RUN", Set.of("reportId"), Set.of(), "evaluation.succeeded");
        register(rules, "EVALUATION_RUN", Set.of("reasonCode"), Set.of(),
                "evaluation.manual_review_required");
        register(rules, "EVALUATION_RUN", Set.of("failureCode"), Set.of(),
                "evaluation.failed_retryable", "evaluation.failed_final");
        register(rules, "EVALUATION_RUN", Set.of("stage"), Set.of(),
                "evaluation.retry_started", "evaluation.cancelled");
        register(rules, "EVALUATION_REPORT", Set.of("evaluationId", "reportVersionId", "state"), Set.of(),
                "evaluation.report.published");
        register(rules, "EVALUATION_REPORT", Set.of("evaluationId", "feedbackId", "feedbackType"), Set.of(),
                "evaluation.report.feedback_appended");

        register(rules, "LEARNING_PLAN",
                Set.of("sourceReportId", "sourceReportVersionId", "configVersionId", "itemCount"), Set.of(),
                "learning.plan.candidate_created");
        register(rules, "LEARNING_PLAN", Set.of(), Set.of(),
                "learning.plan.confirmed", "learning.plan.cancelled");
        register(rules, "LEARNING_PLAN", Set.of(), Set.of("reasonCode"),
                "learning.plan.completed");
        register(rules, "LEARNING_PLAN", Set.of("itemId", "itemVersion"),
                Set.of("reasonCode", "reasonCodeProvided"),
                "learning.item.complete", "learning.item.skip", "learning.item.reschedule");

        register(rules, "BILLING_ENTITLEMENT", Set.of("source", "unit"), Set.of(),
                "billing.entitlement.granted");
        register(rules, "BILLING_ENTITLEMENT", Set.of(), Set.of(),
                "billing.entitlement.activated", "billing.entitlement.expired", "billing.entitlement.revoked");
        register(rules, "BILLING_ENTITLEMENT", Set.of("unit"), Set.of(),
                "billing.usage.reserved", "billing.usage.settled", "billing.usage.released");
        register(rules, "BILLING_RESERVATION", Set.of("businessOperationId", "unit"), Set.of(),
                "billing.reservation.created");
        register(rules, "BILLING_RESERVATION", Set.of("unit", "settlementId", "ruleVersion"), Set.of(),
                "billing.reservation.settled");
        register(rules, "BILLING_RESERVATION", Set.of("reasonCode"), Set.of(),
                "billing.reservation.released");
        register(rules, "BILLING_RESERVATION", Set.of(), Set.of(), "billing.reservation.expired");

        register(rules, "PLATFORM_JOB", Set.of("jobType", "businessOperationId"), Set.of(),
                "platform.job.scheduled");
        register(rules, "PLATFORM_JOB", Set.of("attemptNo", "workerId"), Set.of(), Set.of("attemptNo"),
                "platform.job.claimed");
        register(rules, "PLATFORM_JOB", Set.of("attemptNo"), Set.of(),
                "platform.job.succeeded", "platform.job.retry_scheduled", "platform.job.cancel_requested",
                "platform.job.lease_expired");
        register(rules, "PLATFORM_JOB", Set.of("attemptNo"), Set.of("errorCode"),
                "platform.job.failed_retryable", "platform.job.failed_final");
        register(rules, "PLATFORM_JOB", Set.of(), Set.of("attemptNo"),
                "platform.job.cancelled");
        register(rules, "PLATFORM_JOB", Set.of("attemptNo", "reconciliationReceiptId"), Set.of(),
                "platform.job.cancellation_reconciled", "platform.job.success_reconciled");

        register(rules, "VOICE_AUDIO_ARTIFACT", Set.of("sessionId", "turnId", "purpose"), Set.of(),
                "voice.audio_artifact.created");
        register(rules, "VOICE_AUDIO_ARTIFACT", Set.of("codec"), Set.of(),
                "voice.audio_artifact.upload_started", "voice.audio_artifact.synthesis_started");
        register(rules, "VOICE_AUDIO_ARTIFACT", Set.of("bytes", "durationMillis"), Set.of(),
                "voice.audio_artifact.uploaded");
        register(rules, "VOICE_AUDIO_ARTIFACT", Set.of("providerInvocationId"), Set.of(),
                "voice.audio_artifact.transcribed");
        register(rules, "VOICE_AUDIO_ARTIFACT",
                Set.of("bytes", "durationMillis", "providerInvocationId"), Set.of(),
                "voice.audio_artifact.synthesized");
        register(rules, "VOICE_AUDIO_ARTIFACT", Set.of("failureCode"), Set.of(),
                "voice.audio_artifact.upload_failed", "voice.audio_artifact.transcription_failed",
                "voice.audio_artifact.synthesis_failed", "voice.audio_artifact.delete_partial");
        register(rules, "VOICE_AUDIO_ARTIFACT", Set.of(), Set.of(),
                "voice.audio_artifact.transcription_started", "voice.audio_artifact.delete_queued",
                "voice.audio_artifact.deleted", "voice.audio_artifact.delete_retried");
        register(rules, "VOICE_TRANSCRIPT", Set.of("audioArtifactId"), Set.of(),
                "voice.transcript.opened");
        register(rules, "VOICE_TRANSCRIPT", Set.of("transcriptVersionId"), Set.of(),
                "voice.transcript.asr_final", "voice.transcript.corrected");
        register(rules, "VOICE_TRANSCRIPT", Set.of("transcriptVersionId", "confirmedBy"), Set.of(),
                "voice.transcript.confirmed");
        register(rules, "VOICE_TRANSCRIPT", Set.of(), Set.of(), "voice.transcript.cancelled");

        return Map.copyOf(rules);
    }

    private static void register(
            Map<String, Rule> rules,
            String aggregateType,
            Set<String> required,
            Set<String> optional,
            String... eventTypes
    ) {
        Set<String> published = new java.util.HashSet<>(required);
        published.addAll(optional);
        register(rules, aggregateType, required, optional, Set.copyOf(published), eventTypes);
    }

    private static void register(
            Map<String, Rule> rules,
            String aggregateType,
            Set<String> required,
            Set<String> optional,
            Set<String> published,
            String... eventTypes
    ) {
        Rule rule = new Rule(aggregateType, required, optional, published);
        for (String eventType : eventTypes) {
            if (rules.put(eventType, rule) != null) {
                throw new IllegalStateException("duplicate domain event rule: " + eventType);
            }
        }
    }

    private record Rule(
            String aggregateType,
            Set<String> requiredKeys,
            Set<String> optionalKeys,
            Set<String> publishedKeys
    ) {
        private Rule {
            aggregateType = java.util.Objects.requireNonNull(aggregateType);
            requiredKeys = Set.copyOf(requiredKeys);
            optionalKeys = Set.copyOf(optionalKeys);
            publishedKeys = Set.copyOf(publishedKeys);
            Set<String> allowed = new java.util.HashSet<>(requiredKeys);
            allowed.addAll(optionalKeys);
            if (!allowed.containsAll(publishedKeys)) {
                throw new IllegalArgumentException("published event keys must be input-allowlisted");
            }
        }
    }
}
