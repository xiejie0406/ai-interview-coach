package com.ruoyi.interview.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * RuoYi 唯一容器中的语音接口边界。
 *
 * 当前 PostgreSQL repository、对象存储和 Provider 尚未接入，因此不会伪造
 * session/ticket/音频/转写成功；接口会返回可恢复的明确未就绪结果。
 */
@Validated
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
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public PreflightResponse preflight(@PathVariable UUID interviewId,
                                       @Valid @RequestBody PreflightRequest request) {
        principals.requiredPrincipal();
        // 这是能力探测，不创建任何业务事实；缺少 persistence/provider 时明确降级。
        return new PreflightResponse(false, false, List.of(), 120, 12L * 1024 * 1024,
                "VOICE_NOT_READY");
    }

    @PostMapping(path = "/interviews/{interviewId}/voice-sessions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('interview:session:start') && @ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> openSession(@PathVariable UUID interviewId,
                                                            @Valid @RequestBody OpenSessionRequest request,
                                                            @RequestHeader(value = "Idempotency-Key", required = false)
                                                            String idempotencyKey) {
        principals.requiredPrincipal();
        return notReady("VOICE_NOT_READY",
                "语音会话暂未就绪：PostgreSQL 音频持久化、RuoYi 同源 WebSocket 编排和 ASR Provider 尚未接入");
    }

    @GetMapping("/transcripts/{transcriptId}")
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> transcript(@PathVariable UUID transcriptId) {
        principals.requiredPrincipal();
        return notReady("VOICE_NOT_READY", "语音转写持久化尚未就绪");
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
        return notReady("VOICE_NOT_READY", "转写确认与回答原子提交尚未就绪");
    }

    @GetMapping("/audio-artifacts/{artifactId}")
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> artifact(@PathVariable UUID artifactId) {
        principals.requiredPrincipal();
        return notReady("VOICE_NOT_READY", "音频 Artifact 持久化尚未就绪");
    }

    @PostMapping("/audio-artifacts/{artifactId}/commands/delete")
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> deleteArtifact(@PathVariable UUID artifactId,
                                                              @RequestHeader(value = "If-Match", required = false)
                                                              String ifMatch,
                                                              @RequestHeader(value = "Idempotency-Key", required = false)
                                                              String idempotencyKey) {
        principals.requiredPrincipal();
        return notReady("VOICE_NOT_READY", "音频 Artifact 删除编排尚未就绪");
    }

    private static ResponseEntity<AjaxResult> notReady(String code, String message) {
        AjaxResult result = AjaxResult.error(HttpStatus.NOT_IMPLEMENTED.value(), message);
        result.put("errorCode", code);
        result.put("retryable", false);
        result.put("data", Map.of("recoverableAction", "TEXT_FALLBACK"));
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(result);
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
