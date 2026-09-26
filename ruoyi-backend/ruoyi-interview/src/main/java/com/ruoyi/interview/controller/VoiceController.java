package com.ruoyi.interview.controller;

import com.ruoyi.interview.configuration.InterviewEnabled;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ruoyi.interview.configuration.RuoYiPrincipalFacade;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.interview.infrastructure.AdapterUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * RuoYi 唯一容器中的语音接口边界。
 *
 * business-rest-endpoints-enabled=false 时的互斥 fallback；不会与已接入用例的
 * rest/voice Controller 同时注册，也不会伪造 session/ticket/音频/转写成功。
 */
@Deprecated
@Validated
@InterviewEnabled
@RestController
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "business-rest-endpoints-enabled",
        havingValue = "false", matchIfMissing = true)
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class VoiceController {

    private final RuoYiPrincipalFacade principals;

    public VoiceController(RuoYiPrincipalFacade principals) {
        this.principals = principals;
    }

    @PostMapping(path = "/interviews/{interviewId}/voice-preflight",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:session:start') && @ss.hasPermi('interview:voice:upload')")
    public PreflightResponse preflight(@PathVariable UUID interviewId,
                                       @Valid @RequestBody PreflightRequest request) {
        principals.requiredPrincipal();
        throw unavailable();
    }

    @PostMapping(path = "/interviews/{interviewId}/voice-sessions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:session:start') && @ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> openSession(@PathVariable UUID interviewId,
                                                            @Valid @RequestBody OpenSessionRequest request,
                                                            @RequestHeader(value = "Idempotency-Key", required = false)
                                                            String idempotencyKey) {
        principals.requiredPrincipal();
        throw unavailable();
    }

    @GetMapping("/transcripts/{transcriptId}")
    @PreAuthorize("@ss.hasPermi('interview:session:recover')")
    public ResponseEntity<AjaxResult> transcript(@PathVariable UUID transcriptId) {
        principals.requiredPrincipal();
        throw unavailable();
    }

    @PostMapping(path = "/transcripts/{transcriptId}/commands/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:voice:confirm') && @ss.hasPermi('interview:session:submit')")
    public ResponseEntity<AjaxResult> confirmTranscript(
            @PathVariable UUID transcriptId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ConfirmTranscriptRequest request) {
        principals.requiredPrincipal();
        throw unavailable();
    }

    @GetMapping("/audio-artifacts/{artifactId}")
    @PreAuthorize("@ss.hasPermi('interview:session:recover')")
    public ResponseEntity<AjaxResult> artifact(@PathVariable UUID artifactId) {
        principals.requiredPrincipal();
        throw unavailable();
    }

    @PostMapping("/audio-artifacts/{artifactId}/commands/delete")
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> deleteArtifact(@PathVariable UUID artifactId,
                                                              @RequestHeader(value = "If-Match", required = false)
                                                              String ifMatch,
                                                              @RequestHeader(value = "Idempotency-Key", required = false)
                                                              String idempotencyKey) {
        principals.requiredPrincipal();
        throw unavailable();
    }

    private static AdapterUnavailableException unavailable() {
        return new AdapterUnavailableException("voice-runtime");
    }

    public record PreflightRequest(@NotNull UUID turnId, @NotEmpty List<String> codecCandidates) { }
    public record OpenSessionRequest(@NotNull UUID turnId, String codec, long expectedSessionVersion) { }
    public record ConfirmTranscriptRequest(@NotNull UUID transcriptVersionId,
                                           String correctedText,
                                           boolean lowConfidenceAcknowledged) { }
    public record PreflightResponse(boolean enabled, boolean consentRequired,
                                    List<String> supportedCodecs, int maxDurationSeconds,
                                    long maxBytes, String unavailableReasonCode) { }
}
