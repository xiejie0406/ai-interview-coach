package com.ruoyi.interview.controller.rest.catalog;

import com.ruoyi.interview.controller.rest.common.RequestContextFactory;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.application.catalog.PublishedQuestionSnapshot;
import com.ruoyi.interview.application.catalog.PublishedQuestionSummary;
import com.ruoyi.interview.application.catalog.QuestionSearchCriteria;
import com.ruoyi.interview.application.catalog.SearchPublishedQuestions;
import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import com.ruoyi.interview.application.catalog.port.QuestionPersonalizationPort;
import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catalog HTTP 契约 owner。当前 application 模型尚不能无损形成公开 category/detail 或 Admin detail，
 * 因此逐 operation 返回稳定 501；绝不从 Controller 直读 Repository 或拼造缺失字段。
 */
@Validated
@RestController
@PreAuthorize("@ss.hasPermi('interview:question:list')")
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "catalog-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogController {

    private final SearchPublishedQuestions searchPublishedQuestions;
    private final PublishedQuestionPort publishedQuestions;
    private final QuestionPersonalizationPort personalization;
    private final RequestContextFactory contexts;
    private final TenantId publicTenantId;

    public CatalogController(
            SearchPublishedQuestions searchPublishedQuestions,
            PublishedQuestionPort publishedQuestions,
            QuestionPersonalizationPort personalization,
            RequestContextFactory contexts,
            org.springframework.core.env.Environment environment
    ) {
        this.searchPublishedQuestions = searchPublishedQuestions;
        this.publishedQuestions = publishedQuestions;
        this.personalization = personalization;
        this.contexts = contexts;
        String tenantId = environment.getProperty("interview.catalog.public-tenant-id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("interview.catalog.public-tenant-id must be configured");
        }
        this.publicTenantId = TenantId.of(tenantId);
    }

    @GetMapping("/questions")
    public QuestionPage searchPublishedQuestions(
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) @Size(max = 96) String category,
            @RequestParam(required = false) @Pattern(regexp = "JUNIOR|MID|SENIOR") String difficulty,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        CursorPage<PublishedQuestionSummary> page = searchPublishedQuestions.handle(
                publicTenantId,
                new QuestionSearchCriteria(
                        java.util.Optional.ofNullable(query).filter(value -> !value.isBlank()),
                        java.util.Set.of(),
                        difficulty == null || difficulty.isBlank() ? java.util.Set.of() : java.util.Set.of(difficulty),
                        category == null || category.isBlank() ? java.util.Set.of() : java.util.Set.of(category),
                        java.util.Optional.of("zh-CN"),
                        java.util.Optional.ofNullable(cursor).filter(value -> !value.isBlank()), limit));
        return QuestionPage.from(page);
    }

    @GetMapping("/questions/{questionId}")
    public QuestionDetail getPublishedQuestion(@PathVariable UUID questionId, HttpServletRequest request) {
        PublishedQuestionSnapshot snapshot = publishedQuestions.findPublished(
                        publicTenantId, ResourceId.of(questionId.toString()))
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "published question was not found", false, Map.of("questionId", questionId.toString())));
        java.util.Optional<String> userAnswer = optionalPrincipal(request)
                .flatMap(principal -> personalization.findUserAnswer(publicTenantId,
                principal.tenantId(), principal.userId(), ResourceId.of(questionId.toString())));
        return QuestionDetail.from(snapshot, userAnswer);
    }

    @PostMapping(path = "/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:add')")
    public ResponseEntity<CreatePublicQuestionResponse> createPublicQuestion(
            @Valid @RequestBody CreatePublicQuestionRequest body, HttpServletRequest request) {
        var principal = contexts.requiredPrincipal();
        ResourceId id = personalization.createPublicQuestion(new QuestionPersonalizationPort.CreatePublicQuestion(
                publicTenantId, principal.tenantId(), principal.userId(), body.category(), body.difficulty(), body.title(),
                body.prompt(), body.systemAnswer(), java.time.Instant.now()));
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(new CreatePublicQuestionResponse(id.value()));
    }

    @PutMapping(path = "/questions/{questionId}/my-answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:edit')")
    public ResponseEntity<Void> saveMyAnswer(@PathVariable UUID questionId,
                                              @Valid @RequestBody SaveMyAnswerRequest body,
                                              HttpServletRequest request) {
        var principal = contexts.requiredPrincipal();
        ResourceId id = ResourceId.of(questionId.toString());
        if (publishedQuestions.findPublished(publicTenantId, id).isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.NOT_FOUND, "published question was not found",
                    false, Map.of("questionId", questionId.toString()));
        }
        personalization.saveUserAnswer(publicTenantId, principal.tenantId(), principal.userId(), id, body.answer(),
                java.time.Instant.now());
        return ResponseEntity.noContent().build();
    }

    private java.util.Optional<com.ruoyi.interview.domain.platform.PrincipalRef> optionalPrincipal(
            HttpServletRequest request) {
        try {
            return java.util.Optional.of(contexts.requiredPrincipal());
        } catch (RuntimeException exception) {
            if (com.ruoyi.common.utils.SecurityUtils.getAuthentication() == null) {
                return java.util.Optional.empty();
            }
            throw exception;
        }
    }

    @GetMapping("/admin/questions")
    public ResponseEntity<Void> listAdminQuestions(
            @RequestParam(required = false)
            @Pattern(regexp = "DRAFT|IN_REVIEW|PUBLISHED|PUBLISHED_WITH_DRAFT|PUBLISHED_WITH_REVIEW|RETIRED")
            String state,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        throw unavailable("listAdminQuestions", "CATALOG_ADMIN_QUERY_USE_CASE_MISSING");
    }

    @PostMapping(path = "/admin/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:add')")
    public ResponseEntity<Void> createQuestionDraft(
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody CreateQuestionDraftRequest request
    ) {
        throw unavailable("createQuestionDraft", "CATALOG_ADMIN_DETAIL_RESULT_USE_CASE_MISSING");
    }

    @GetMapping("/admin/questions/{questionId}")
    public ResponseEntity<Void> getAdminQuestion(@PathVariable UUID questionId) {
        throw unavailable("getAdminQuestion", "CATALOG_ADMIN_DETAIL_USE_CASE_MISSING");
    }

    @PostMapping(path = "/admin/questions/{questionId}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:edit')")
    public ResponseEntity<Void> createQuestionVersion(
            @PathVariable UUID questionId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody QuestionDraftContent request
    ) {
        throw unavailable("createQuestionVersion", "CATALOG_VERSION_BY_ID_USE_CASE_MISSING");
    }

    @PostMapping(path = "/admin/questions/{questionId}/rubrics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:edit')")
    public ResponseEntity<Void> createRubricVersion(
            @PathVariable UUID questionId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody CreateRubricVersionRequest request
    ) {
        throw unavailable("createRubricVersion", "CATALOG_RUBRIC_VIEW_RESULT_USE_CASE_MISSING");
    }

    @PostMapping(path = "/admin/questions/{questionId}/commands/{command}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:edit')")
    public ResponseEntity<Void> applyQuestionWorkflowCommand(
            @PathVariable UUID questionId,
            @PathVariable @Pattern(regexp = "submit-review|reject-review|publish|retire") String command,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody QuestionWorkflowCommandRequest request
    ) {
        throw unavailable("applyQuestionWorkflowCommand", "CATALOG_ADMIN_DETAIL_RESULT_USE_CASE_MISSING");
    }

    private static ApplicationException unavailable(String operationId, String reasonCode) {
        return new ApplicationException(ApplicationErrorCode.CAPABILITY_UNAVAILABLE,
                "catalog operation is unavailable until its application contract is complete", false,
                Map.of("operationId", operationId, "reasonCode", reasonCode));
    }

    public record QuestionPage(List<QuestionSummary> items, String nextCursor, boolean hasMore) {
        static QuestionPage from(CursorPage<PublishedQuestionSummary> page) {
            return new QuestionPage(page.items().stream().map(QuestionSummary::from).toList(),
                    page.nextCursor().orElse(null), page.nextCursor().isPresent());
        }
    }

    public record QuestionSummary(
            String id, String versionId, String title, String category,
            String difficulty, Integer estimatedMinutes, String status, int version
    ) {
        static QuestionSummary from(PublishedQuestionSummary summary) {
            return new QuestionSummary(summary.questionId().value(),
                    summary.questionVersion().resourceId().value(), summary.title(),
                    summary.targetRoles().isEmpty() ? "GENERAL" : summary.targetRoles().getFirst(),
                    summary.difficulty(), 15, "PUBLISHED", summary.questionVersion().versionNo());
        }
    }

    public record QuestionDetail(
            String id, String versionId, String title, String category,
            String difficulty, Integer estimatedMinutes, String status, int version,
            String prompt, List<String> referenceAnswer, List<String> systemAnswer, boolean hasUserAnswer
    ) {
        static QuestionDetail from(PublishedQuestionSnapshot snapshot, java.util.Optional<String> userAnswer) {
            List<String> displayed = userAnswer.map(List::of).orElse(snapshot.answerPoints());
            return new QuestionDetail(snapshot.questionId().value(),
                    snapshot.questionVersion().resourceId().value(), snapshot.title(),
                    snapshot.targetRoles().isEmpty() ? "GENERAL" : snapshot.targetRoles().getFirst(),
                    snapshot.difficulty(), 15, "PUBLISHED", snapshot.questionVersion().versionNo(),
                    snapshot.stem(), displayed, snapshot.answerPoints(), userAnswer.isPresent());
        }
    }

    public record CreatePublicQuestionRequest(
            @NotBlank @Size(max = 96) String category,
            @NotBlank @Pattern(regexp = "JUNIOR|MID|SENIOR") String difficulty,
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 8_000) String prompt,
            @NotBlank @Size(max = 20_000) String systemAnswer
    ) { }

    public record CreatePublicQuestionResponse(String id) { }

    public record SaveMyAnswerRequest(@NotBlank @Size(max = 20_000) String answer) { }

    public record CreateQuestionDraftRequest(
            @NotBlank @Size(max = 160) String stableKey,
            @NotNull @Valid QuestionDraftContent content
    ) { }

    public record QuestionDraftContent(
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 8_000) String stem,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 1_000) String> answerPoints,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 1_000) String> misconceptions,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 1_000) String> followUpTemplates,
            @NotBlank @Pattern(regexp = "JUNIOR|MID|SENIOR") String difficulty,
            @NotEmpty List<@Pattern(regexp = "JAVA_BACKEND|AI_APPLICATION|AGENT_ENGINEER") String> targetRoles,
            @NotBlank @Pattern(regexp = "zh-CN") String locale,
            UUID contentSourceVersionId
    ) { }

    public record CreateRubricVersionRequest(
            @NotNull UUID questionVersionId,
            @NotEmpty List<@Valid RubricDimensionRequest> dimensions,
            @NotBlank @Size(max = 3_000) String refusalPolicy
    ) { }

    public record RubricDimensionRequest(
            @NotBlank @Size(max = 96) String code,
            @NotBlank @Size(max = 1_000) String description,
            boolean evidenceRequired,
            @NotEmpty List<@NotBlank @Size(max = 1_000) String> criteria
    ) { }

    public record QuestionWorkflowCommandRequest(
            UUID questionVersionId,
            UUID rubricVersionId,
            @NotBlank @Size(max = 96) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,95}") String reasonCode
    ) { }
}

