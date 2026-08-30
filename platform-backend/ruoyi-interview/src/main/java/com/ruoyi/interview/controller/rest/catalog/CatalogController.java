package com.ruoyi.interview.controller.rest.catalog;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.interview.controller.rest.common.HttpVersionPreconditions;
import com.ruoyi.interview.controller.rest.common.RequestContextFactory;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;
import com.ruoyi.interview.application.catalog.AdminQuestionDetail;
import com.ruoyi.interview.application.catalog.AdminQuestionSummary;
import com.ruoyi.interview.application.catalog.DraftQuestion;
import com.ruoyi.interview.application.catalog.DraftQuestionVersion;
import com.ruoyi.interview.application.catalog.DraftRubric;
import com.ruoyi.interview.application.catalog.PublishedQuestionSnapshot;
import com.ruoyi.interview.application.catalog.PublishedQuestionSummary;
import com.ruoyi.interview.application.catalog.PublishQuestion;
import com.ruoyi.interview.application.catalog.QuestionSearchCriteria;
import com.ruoyi.interview.application.catalog.QueryAdminCatalog;
import com.ruoyi.interview.application.catalog.RejectQuestionReview;
import com.ruoyi.interview.application.catalog.RetireQuestion;
import com.ruoyi.interview.application.catalog.SearchPublishedQuestions;
import com.ruoyi.interview.application.catalog.SubmitQuestionForReview;
import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import com.ruoyi.interview.application.catalog.port.QuestionPersonalizationPort;
import com.ruoyi.interview.application.shared.CursorPage;
import com.ruoyi.interview.domain.catalog.QuestionStatus;
import com.ruoyi.interview.domain.catalog.QuestionCategory;
import com.ruoyi.interview.domain.catalog.QuestionVersion;
import com.ruoyi.interview.domain.catalog.RubricDimension;
import com.ruoyi.interview.domain.catalog.RubricVersion;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
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
import java.util.Optional;
import java.util.UUID;

/** Catalog HTTP 契约 owner；公开查询、个人答案和 Admin 内容治理共用服务端公共题库租户。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "catalog-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogController {

    private final SearchPublishedQuestions searchPublishedQuestions;
    private final PublishedQuestionPort publishedQuestions;
    private final QuestionPersonalizationPort personalization;
    private final QueryAdminCatalog adminCatalog;
    private final DraftQuestion draftQuestion;
    private final DraftQuestionVersion draftQuestionVersion;
    private final DraftRubric draftRubric;
    private final SubmitQuestionForReview submitQuestionForReview;
    private final RejectQuestionReview rejectQuestionReview;
    private final PublishQuestion publishQuestion;
    private final RetireQuestion retireQuestion;
    private final RequestContextFactory contexts;
    private final TenantId publicTenantId;

    public CatalogController(
            SearchPublishedQuestions searchPublishedQuestions,
            PublishedQuestionPort publishedQuestions,
            QuestionPersonalizationPort personalization,
            QueryAdminCatalog adminCatalog,
            DraftQuestion draftQuestion,
            DraftQuestionVersion draftQuestionVersion,
            DraftRubric draftRubric,
            SubmitQuestionForReview submitQuestionForReview,
            RejectQuestionReview rejectQuestionReview,
            PublishQuestion publishQuestion,
            RetireQuestion retireQuestion,
            RequestContextFactory contexts,
            org.springframework.core.env.Environment environment
    ) {
        this.searchPublishedQuestions = searchPublishedQuestions;
        this.publishedQuestions = publishedQuestions;
        this.personalization = personalization;
        this.adminCatalog = adminCatalog;
        this.draftQuestion = draftQuestion;
        this.draftQuestionVersion = draftQuestionVersion;
        this.draftRubric = draftRubric;
        this.submitQuestionForReview = submitQuestionForReview;
        this.rejectQuestionReview = rejectQuestionReview;
        this.publishQuestion = publishQuestion;
        this.retireQuestion = retireQuestion;
        this.contexts = contexts;
        String tenantId = environment.getProperty("interview.catalog.public-tenant-id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("interview.catalog.public-tenant-id must be configured");
        }
        this.publicTenantId = TenantId.of(tenantId);
    }

    @GetMapping("/questions")
    @Anonymous
    public QuestionPage searchPublishedQuestions(
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) @Size(max = 96) @Pattern(regexp = QuestionCategory.PUBLIC_CATEGORY_PATTERN) String category,
            @RequestParam(required = false) @Pattern(regexp = "JUNIOR|MID|SENIOR") String difficulty,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        CursorPage<PublishedQuestionSummary> page = searchPublishedQuestions.handle(
                publicTenantId,
                new QuestionSearchCriteria(
                        java.util.Optional.ofNullable(query).filter(value -> !value.isBlank()),
                        category == null || category.isBlank() ? java.util.Set.of() : java.util.Set.of(category),
                        difficulty == null || difficulty.isBlank() ? java.util.Set.of() : java.util.Set.of(difficulty),
                        java.util.Set.of(),
                        java.util.Optional.of("zh-CN"),
                        java.util.Optional.ofNullable(cursor).filter(value -> !value.isBlank()), limit));
        return QuestionPage.from(page);
    }

    @GetMapping("/questions/{questionId}")
    @Anonymous
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

    @PutMapping(path = "/questions/{questionId}/my-answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
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
        var authentication = com.ruoyi.common.utils.SecurityUtils.getAuthentication();
        // Spring Security installs an authenticated AnonymousAuthenticationToken for anonymous requests;
        // only a real RuoYi LoginUser may enter the personal-answer lookup path.
        if (authentication == null
                || !(authentication.getPrincipal() instanceof com.ruoyi.common.core.domain.model.LoginUser)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(contexts.requiredPrincipal());
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    @GetMapping("/admin/questions")
    @PreAuthorize("@ss.hasPermi('interview:question:list')")
    public AdminQuestionPage listAdminQuestions(
            @RequestParam(required = false)
            @Pattern(regexp = "DRAFT|IN_REVIEW|PUBLISHED|PUBLISHED_WITH_DRAFT|PUBLISHED_WITH_REVIEW|RETIRED")
            String state,
            @RequestParam(required = false) @Size(max = 96)
            @Pattern(regexp = QuestionCategory.STORED_CATEGORY_PATTERN) String category,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest httpRequest
    ) {
        var context = contexts.operationWithoutRequiredIdempotency(httpRequest);
        return AdminQuestionPage.from(adminCatalog.search(publicTenantId,
                Optional.ofNullable(category).filter(value -> !value.isBlank()), parseState(state),
                Optional.ofNullable(cursor).filter(value -> !value.isBlank()), limit, context));
    }

    @PostMapping(path = "/admin/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:add')")
    public ResponseEntity<AdminQuestionDetailView> createQuestionDraft(
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody CreateQuestionDraftRequest body,
            HttpServletRequest httpRequest
    ) {
        var context = contexts.operation(httpRequest);
        var content = body.content();
        DraftQuestion.Result result = draftQuestion.handle(publicTenantId, new DraftQuestion.Command(
                body.stableKey(), content.category(), content.title(), content.stem(), content.answerPoints(),
                content.misconceptions(), content.followUpTemplates(), content.difficulty(),
                content.targetRoles(), content.locale(),
                Optional.ofNullable(content.contentSourceVersionId()).map(id -> ResourceId.of(id.toString())),
                context));
        AdminQuestionDetail detail = adminCatalog.get(publicTenantId, result.questionId(), context);
        return ResponseEntity.status(201).eTag(HttpVersionPreconditions.etag(detail.question().aggregateVersion()))
                .body(AdminQuestionDetailView.from(detail));
    }

    @GetMapping("/admin/questions/{questionId}")
    @PreAuthorize("@ss.hasPermi('interview:question:list')")
    public ResponseEntity<AdminQuestionDetailView> getAdminQuestion(@PathVariable UUID questionId,
                                                                      HttpServletRequest httpRequest) {
        var context = contexts.operationWithoutRequiredIdempotency(httpRequest);
        AdminQuestionDetail detail = adminCatalog.get(publicTenantId, ResourceId.of(questionId.toString()), context);
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(detail.question().aggregateVersion()))
                .body(AdminQuestionDetailView.from(detail));
    }

    @PostMapping(path = "/admin/questions/{questionId}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:edit')")
    public ResponseEntity<AdminQuestionDetailView> createQuestionVersion(
            @PathVariable UUID questionId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody QuestionDraftContent body,
            HttpServletRequest httpRequest
    ) {
        var context = contexts.operation(httpRequest);
        draftQuestionVersion.handle(publicTenantId, new DraftQuestionVersion.Command(
                ResourceId.of(questionId.toString()), body.category(), body.title(), body.stem(), body.answerPoints(),
                body.misconceptions(), body.followUpTemplates(), body.difficulty(), body.targetRoles(),
                body.locale(), Optional.ofNullable(body.contentSourceVersionId()).map(id -> ResourceId.of(id.toString())),
                HttpVersionPreconditions.requireIfMatch(ifMatch), context));
        AdminQuestionDetail detail = adminCatalog.get(publicTenantId, ResourceId.of(questionId.toString()), context);
        return ResponseEntity.status(201).eTag(HttpVersionPreconditions.etag(detail.question().aggregateVersion()))
                .body(AdminQuestionDetailView.from(detail));
    }

    @PostMapping(path = "/admin/questions/{questionId}/rubrics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:question:edit')")
    public ResponseEntity<RubricVersionView> createRubricVersion(
            @PathVariable UUID questionId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody CreateRubricVersionRequest body,
            HttpServletRequest httpRequest
    ) {
        var context = contexts.operation(httpRequest);
        DraftRubric.Result result = draftRubric.handle(publicTenantId, new DraftRubric.Command(
                ResourceId.of(questionId.toString()), ResourceId.of(body.questionVersionId().toString()),
                body.dimensions().stream().map(CatalogController::toDomainDimension).toList(),
                body.refusalPolicy(), HttpVersionPreconditions.requireIfMatch(ifMatch), context));
        RubricVersion rubric = adminCatalog.getRubricVersion(publicTenantId, result.rubricVersionId(), context)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                        "created rubric version is not readable", false,
                        Map.of("rubricVersionId", result.rubricVersionId().value())));
        AdminQuestionDetail detail = adminCatalog.get(publicTenantId, ResourceId.of(questionId.toString()), context);
        return ResponseEntity.status(201)
                .eTag(HttpVersionPreconditions.etag(detail.question().aggregateVersion()))
                .body(RubricVersionView.from(rubric));
    }

    @PostMapping(path = "/admin/questions/{questionId}/commands/{command}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("(#command == 'submit-review' && @ss.hasPermi('interview:question:edit')) || "
            + "(#command != 'submit-review' && @ss.hasPermi('interview:question:review'))")
    public ResponseEntity<AdminQuestionDetailView> applyQuestionWorkflowCommand(
            @PathVariable UUID questionId,
            @PathVariable @Pattern(regexp = "submit-review|reject-review|publish|retire") String command,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @Pattern(regexp = "^\"v[0-9]+\"$") String ifMatch,
            @Valid @RequestBody QuestionWorkflowCommandRequest body,
            HttpServletRequest httpRequest
    ) {
        var context = contexts.operation(httpRequest);
        ResourceId id = ResourceId.of(questionId.toString());
        AggregateVersion expected = HttpVersionPreconditions.requireIfMatch(ifMatch);
        switch (command) {
            case "submit-review" -> submitQuestionForReview.handle(publicTenantId,
                    new SubmitQuestionForReview.Command(id, expected, context));
            case "reject-review" -> rejectQuestionReview.handle(publicTenantId,
                    new RejectQuestionReview.Command(id, body.reasonCode(), expected, context));
            case "publish" -> publishQuestion.handle(publicTenantId,
                    new PublishQuestion.Command(id, requiredResourceId(body.questionVersionId(), "questionVersionId"),
                            requiredResourceId(body.rubricVersionId(), "rubricVersionId"), expected,
                            body.reasonCode(), context));
            case "retire" -> retireQuestion.handle(publicTenantId,
                    new RetireQuestion.Command(id, body.reasonCode(), expected, context));
            default -> throw new ApplicationException(ApplicationErrorCode.INTERNAL_CONSISTENCY_ERROR,
                    "unsupported question workflow command", false, Map.of("command", command));
        }
        AdminQuestionDetail detail = adminCatalog.get(publicTenantId, id, context);
        return ResponseEntity.ok().eTag(HttpVersionPreconditions.etag(detail.question().aggregateVersion()))
                .body(AdminQuestionDetailView.from(detail));
    }

    /** Admin 列表响应；nextCursor 为 null 表示没有下一页。 */
    public record AdminQuestionPage(
            List<AdminQuestionSummaryView> items,
            String nextCursor
    ) {
        public AdminQuestionPage {
            items = List.copyOf(items == null ? List.of() : items);
        }

        static AdminQuestionPage from(CursorPage<AdminQuestionSummary> page) {
            return new AdminQuestionPage(
                    page.items().stream().map(AdminQuestionSummaryView::from).toList(),
                    page.nextCursor().orElse(null));
        }
    }

    /** Admin 题目根投影；正文不放在列表中。 */
    public record AdminQuestionSummaryView(
            String id,
            String stableKey,
            String state,
            ImmutableVersionRefView currentDraftVersion,
            ImmutableVersionRefView currentPublishedVersion,
            long version
    ) {
        static AdminQuestionSummaryView from(AdminQuestionSummary summary) {
            return new AdminQuestionSummaryView(
                    summary.questionId().value(),
                    summary.stableKey(),
                    summary.state().name(),
                    summary.currentDraftVersion().map(ImmutableVersionRefView::from).orElse(null),
                    summary.currentPublishedVersion().map(ImmutableVersionRefView::from).orElse(null),
                    summary.aggregateVersion().value());
        }
    }

    /** 不可变版本指针的公开安全投影。 */
    public record ImmutableVersionRefView(
            String id,
            int versionNo,
            String contentHash
    ) {
        static ImmutableVersionRefView from(ImmutableVersionRef reference) {
            return new ImmutableVersionRefView(reference.resourceId().value(), reference.versionNo(),
                    reference.contentHash());
        }
    }

    /** Admin 详情响应；source/license/verification 凭据仅保留 contentSourceVersionId。 */
    public record AdminQuestionDetailView(
            AdminQuestionSummaryView question,
            QuestionVersionView draft,
            QuestionVersionView published,
            RubricVersionView rubric
    ) {
        static AdminQuestionDetailView from(AdminQuestionDetail detail) {
            return new AdminQuestionDetailView(
                    AdminQuestionSummaryView.from(detail.question()),
                    detail.draft().map(QuestionVersionView::from).orElse(null),
                    detail.published().map(QuestionVersionView::from).orElse(null),
                    detail.rubric().map(RubricVersionView::from).orElse(null));
        }
    }

    /** 题目不可变版本的安全 DTO；不泄露内部 source/license/verification 字段。 */
    public record QuestionVersionView(
            String id,
            int versionNo,
            String contentHash,
            String category,
            String title,
            String stem,
            List<String> answerPoints,
            List<String> misconceptions,
            List<String> followUpTemplates,
            String difficulty,
            List<String> targetRoles,
            String locale,
            String contentSourceVersionId,
            java.time.Instant createdAt
    ) {
        static QuestionVersionView from(QuestionVersion version) {
            return new QuestionVersionView(
                    version.id().value(),
                    version.versionNo(),
                    version.contentHash(),
                    version.category(),
                    version.title(),
                    version.stem(),
                    version.answerPoints(),
                    version.misconceptions(),
                    version.followUpTemplates(),
                    version.difficulty(),
                    version.targetRoles(),
                    version.locale(),
                    version.source().map(source -> source.sourceVersion().resourceId().value()).orElse(null),
                    version.createdAt());
        }
    }

    /** Rubric 不可变版本的公开投影。 */
    public record RubricVersionView(
            String id,
            String questionVersionId,
            int versionNo,
            String contentHash,
            List<RubricDimensionView> dimensions,
            String refusalPolicy,
            java.time.Instant createdAt
    ) {
        static RubricVersionView from(RubricVersion version) {
            return new RubricVersionView(
                    version.id().value(),
                    version.questionVersionId().value(),
                    version.versionNo(),
                    version.contentHash(),
                    version.dimensions().stream().map(RubricDimensionView::from).toList(),
                    version.refusalPolicy(),
                    version.createdAt());
        }
    }

    public record RubricDimensionView(
            String code,
            String description,
            boolean evidenceRequired,
            List<String> criteria
    ) {
        static RubricDimensionView from(RubricDimension dimension) {
            return new RubricDimensionView(dimension.code(), dimension.description(),
                    dimension.evidenceRequired(), dimension.criteria());
        }
    }

    private static Optional<QuestionStatus> parseState(String value) {
        return Optional.ofNullable(value).filter(item -> !item.isBlank()).map(QuestionStatus::valueOf);
    }

    private static ResourceId requiredResourceId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required for publish");
        }
        return ResourceId.of(value.toString());
    }

    private static RubricDimension toDomainDimension(RubricDimensionRequest request) {
        return new RubricDimension(request.code(), request.description(), request.evidenceRequired(), request.criteria());
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
                    summary.category(),
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
                    snapshot.category(),
                    snapshot.difficulty(), 15, "PUBLISHED", snapshot.questionVersion().versionNo(),
                    snapshot.stem(), displayed, snapshot.answerPoints(), userAnswer.isPresent());
        }
    }

    public record SaveMyAnswerRequest(@NotBlank @Size(max = 20_000) String answer) { }

    public record CreateQuestionDraftRequest(
            @NotBlank @Size(max = 160) String stableKey,
            @NotNull @Valid QuestionDraftContent content
    ) { }

    public record QuestionDraftContent(
            @NotBlank @Pattern(regexp = QuestionCategory.NEW_CATEGORY_PATTERN) String category,
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

