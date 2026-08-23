package com.ruoyi.interview.controller.rest.voice;

import com.ruoyi.interview.controller.rest.common.RequestContextFactory;
import com.ruoyi.interview.application.governance.GrantConsent;
import com.ruoyi.interview.application.governance.port.ConsentPolicyRegistryPort;
import com.ruoyi.interview.application.governance.port.ConsentQueryPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.domain.governance.ConsentAction;
import com.ruoyi.interview.domain.governance.ConsentPurpose;
import com.ruoyi.interview.domain.platform.ImmutableVersionRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** privacy-audit OpenAPI 中语音 MVP 所需的 consent projection/grant/revoke。 */
@RestController
@PreAuthorize("@ss.hasPermi('interview:voice:upload')")
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "business-rest-endpoints-enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/consents", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsentController {
    private final GrantConsent grantConsent;
    private final ConsentQueryPort query;
    private final ConsentPolicyRegistryPort policies;
    private final TransactionPort transaction;
    private final RequestContextFactory contexts;

    public ConsentController(GrantConsent grantConsent, ConsentQueryPort query,
                             ConsentPolicyRegistryPort policies, TransactionPort transaction,
                             RequestContextFactory contexts) {
        this.grantConsent = grantConsent;
        this.query = query;
        this.policies = policies;
        this.transaction = transaction;
        this.contexts = contexts;
    }

    @GetMapping
    public List<ConsentResponse> current(HttpServletRequest request) {
        var context = contexts.query(request);
        return Arrays.stream(ConsentPurpose.values()).map(purpose -> {
            var decision = query.current(context.principal().tenantId(), context.principal().userId(),
                    purpose, context.requestedAt());
            return new ConsentResponse(purpose.name(),
                    decision.policyVersion().map(value -> value.resourceId().value()).orElse(null),
                    decision.granted(), decision.effectiveAt().map(Instant::toString).orElse(null),
                    purpose != ConsentPurpose.SERVICE_TERMS);
        }).toList();
    }

    @PostMapping(path = "/{purpose}/grants", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsentResponse> grant(@PathVariable ConsentPurpose purpose,
                                                  @Valid @RequestBody ConsentRequest body,
                                                  HttpServletRequest request) {
        return change(purpose, ConsentAction.GRANTED, body, request);
    }

    @PostMapping(path = "/{purpose}/revocations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConsentResponse> revoke(@PathVariable ConsentPurpose purpose,
                                                   @Valid @RequestBody ConsentRequest body,
                                                   HttpServletRequest request) {
        return change(purpose, ConsentAction.REVOKED, body, request);
    }

    private ResponseEntity<ConsentResponse> change(ConsentPurpose purpose, ConsentAction action,
                                                    ConsentRequest body, HttpServletRequest request) {
        ImmutableVersionRef policy = VoicePolicyCatalog.POLICIES.get(purpose);
        if (policy == null || !policy.resourceId().value().equals(body.policyVersionId())) {
            throw new IllegalArgumentException("语音同意政策版本无效");
        }
        var context = contexts.operation(request);
        var principal = context.requirePrincipal();
        transaction.required(() -> {
            policies.ensureRegistered(principal.tenantId(), purpose, policy, Instant.EPOCH);
            return null;
        });
        var result = grantConsent.handle(new GrantConsent.Command(purpose, action, policy,
                "VOICE_MVP_UI", context));
        return ResponseEntity.status(201).body(new ConsentResponse(purpose.name(),
                policy.resourceId().value(), result.action() == ConsentAction.GRANTED,
                context.requestedAt().toString(), true));
    }

    public record ConsentRequest(@NotBlank String policyVersionId,
                                 @AssertTrue boolean acknowledgement) { }
    public record ConsentResponse(String purpose, String policyVersionId, boolean granted,
                                  String recordedAt, boolean revocable) { }
}

