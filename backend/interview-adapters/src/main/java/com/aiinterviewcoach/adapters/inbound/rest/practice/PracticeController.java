package com.aiinterviewcoach.adapters.inbound.rest.practice;

import com.aiinterviewcoach.adapters.inbound.rest.common.HttpVersionPreconditions;
import com.aiinterviewcoach.adapters.inbound.rest.common.RequestContextFactory;
import com.aiinterviewcoach.application.practice.PracticeAttemptView;
import com.aiinterviewcoach.application.practice.SavePracticeDraft;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.domain.platform.ResourceId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Practice REST owner；只开放能由现有 application result 无损证明的 save-draft operation。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "business-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/practice-attempts", produces = MediaType.APPLICATION_JSON_VALUE)
public class PracticeController {

    private final Optional<SavePracticeDraft> saveDraft;
    private final Optional<RequestContextFactory> contexts;

    public PracticeController(Optional<SavePracticeDraft> saveDraft, Optional<RequestContextFactory> contexts) {
        this.saveDraft = saveDraft == null ? Optional.empty() : saveDraft;
        this.contexts = contexts == null ? Optional.empty() : contexts;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> startPractice(
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody StartPracticeRequest request
    ) {
        throw unavailable("startPractice", "PRACTICE_PUBLISHED_VERSION_RESOLUTION_MISSING");
    }

    @GetMapping
    public ResponseEntity<Void> listPracticeHistory() {
        throw unavailable("listPracticeHistory", "PRACTICE_HISTORY_PAGINATION_CONTRACT_UNRESOLVED");
    }

    @PutMapping(path = "/{attemptId}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PracticeAttemptResponse> savePracticeDraft(
            @PathVariable UUID attemptId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody SaveDraftRequest body,
            HttpServletRequest request
    ) {
        RequestContextFactory contextFactory = contexts.orElseThrow(() ->
                unavailable("savePracticeDraft", "REQUEST_CONTEXT_OWNER_MISSING"));
        SavePracticeDraft useCase = saveDraft.orElseThrow(() ->
                unavailable("savePracticeDraft", "SAVE_PRACTICE_DRAFT_BEAN_MISSING"));
        PracticeAttemptView result = useCase.handle(new SavePracticeDraft.Command(
                ResourceId.of(attemptId), body.text(), HttpVersionPreconditions.requireIfMatch(ifMatch),
                contextFactory.operation(request)));
        PracticeAttemptResponse response = PracticeAttemptResponse.fromDraft(result);
        return ResponseEntity.ok()
                .eTag(HttpVersionPreconditions.etag(result.version()))
                .body(response);
    }

    @PostMapping(path = "/{attemptId}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> submitPracticeAnswer(
            @PathVariable UUID attemptId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody SubmitAnswerRequest request
    ) {
        throw unavailable("submitPracticeAnswer", "PRACTICE_EVALUATION_RECEIPT_CONTRACT_UNRESOLVED");
    }

    private static ApplicationException unavailable(String operationId, String reasonCode) {
        return new ApplicationException(ApplicationErrorCode.CAPABILITY_UNAVAILABLE,
                "practice operation is unavailable until its application contract is complete", false,
                Map.of("operationId", operationId, "reasonCode", reasonCode));
    }

    public record StartPracticeRequest(@NotNull UUID questionVersionId) { }

    public record SaveDraftRequest(@NotNull @Size(max = 20_000) String text) { }

    public record SubmitAnswerRequest(
            @NotNull @Size(min = 1, max = 20_000) String text,
            @Pattern(regexp = "[A-Fa-f0-9]{64}") String contentHash
    ) { }

    public record AnswerVersionResponse(String id, int version, Instant submittedAt) { }

    public record PracticeAttemptResponse(
            String id,
            String questionVersionId,
            String state,
            List<AnswerVersionResponse> answerVersions,
            String evaluationStatus,
            long version
    ) {
        static PracticeAttemptResponse fromDraft(PracticeAttemptView view) {
            if (view.latestAnswerVersionId().isPresent() || view.submittedAt().isPresent()) {
                throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "draft operation returned submitted answer metadata", false, Map.of());
            }
            return new PracticeAttemptResponse(view.attemptId().value(),
                    view.questionVersion().resourceId().value(), view.state().name(), List.of(),
                    "NOT_REQUESTED", view.version().value());
        }

        public PracticeAttemptResponse {
            answerVersions = List.copyOf(answerVersions == null ? List.of() : answerVersions);
        }
    }
}
