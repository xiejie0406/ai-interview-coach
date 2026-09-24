package com.ruoyi.interview.controller.rest.voice;

import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.application.agent.port.TextToSpeechPort;
import com.ruoyi.interview.application.integration.port.ObjectStoragePort;
import com.ruoyi.interview.configuration.properties.VoiceRuntimeProperties;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.persistence.shared.UnavailableSensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.provider.ProviderAdapter;
import com.ruoyi.interview.infrastructure.storage.ObjectStorageAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

/** 只读取本地依赖和已加载配置；不在 GET 中收费、上传音频或探测第三方。 */
@RestController
public class VoiceCapabilitiesController {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectStoragePort storage;
    private final SpeechToTextPort asr;
    private final TextToSpeechPort tts;
    private final SensitiveEnvelopeCipher cipher;
    private final VoiceRuntimeProperties runtime;
    private final ObjectProvider<RedisConnectionFactory> redis;

    public VoiceCapabilitiesController(NamedParameterJdbcTemplate jdbc, ObjectStoragePort storage,
            SpeechToTextPort asr, TextToSpeechPort tts, SensitiveEnvelopeCipher cipher,
            VoiceRuntimeProperties runtime, ObjectProvider<RedisConnectionFactory> redis) {
        this.jdbc = jdbc; this.storage = storage; this.asr = asr; this.tts = tts;
        this.cipher = cipher; this.runtime = runtime; this.redis = redis;
    }

    @GetMapping("/api/v1/voice-capabilities")
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public Map<String, Object> capabilities() {
        var checks = new ArrayList<Map<String, Object>>();
        boolean database;
        try {
            database = Boolean.TRUE.equals(jdbc.queryForObject("""
                select to_regclass('voice.audio_artifact') is not null
                   and to_regclass('voice.transcript_version') is not null
                   and to_regclass('voice.turn_execution') is not null
                   and exists (select 1 from pg_constraint where conrelid='governance.consent_policy_version'::regclass
                       and confrelid='platform.business_tenant'::regclass and contype='f')
                """, Map.of(), Boolean.class));
        } catch (RuntimeException unavailable) { database = false; }
        checks.add(check("DATABASE", "语音数据表", database, "VOICE_SCHEMA_UNAVAILABLE"));
        checks.add(check("STORAGE", "音频存储", storage instanceof ObjectStorageAdapter s && s.available(), "OBJECT_STORAGE_NOT_READY"));
        checks.add(check("ENCRYPTION", "敏感字段加密", !(cipher instanceof UnavailableSensitiveEnvelopeCipher), "SENSITIVE_ENVELOPE_NOT_READY"));
        boolean ticket = "memory".equals(runtime.getTicketStore());
        if ("redis".equals(runtime.getTicketStore())) {
            try {
                var factory = redis.getIfAvailable();
                if (factory != null) {
                    try (var connection = factory.getConnection()) { ticket = "PONG".equals(connection.ping()); }
                }
            } catch (RuntimeException unavailable) { ticket = false; }
        }
        checks.add(check("TICKET", "语音连接票据", ticket, "VOICE_TICKET_UNAVAILABLE"));
        boolean asrReady = asr instanceof ProviderAdapter provider && provider.available();
        boolean ttsReady = tts instanceof ProviderAdapter provider && provider.available();
        checks.add(check("ASR", "语音识别配置", asrReady,
                providerReason(asr, "ASR_NOT_CONFIGURED")));
        checks.add(check("TTS", "语音合成配置", ttsReady,
                providerReason(tts, "TTS_NOT_CONFIGURED")));
        boolean ready = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("ready")));
        var asrHealth = asr instanceof ProviderAdapter p ? p.lastObservation() : Map.of("status", "NOT_CHECKED");
        var ttsHealth = tts instanceof ProviderAdapter p ? p.lastObservation() : Map.of("status", "NOT_CHECKED");
        boolean failed = "FAIL".equals(asrHealth.get("status")) || "FAIL".equals(ttsHealth.get("status"));
        boolean passed = "PASS".equals(asrHealth.get("status")) && "PASS".equals(ttsHealth.get("status"));
        return Map.of("observedAt", Instant.now().toString(), "source", "CONFIGURATION_LOCAL_CHECKS_AND_LAST_CALL",
                "status", !ready || failed ? "VOICE_NOT_READY" : passed ? "LAST_CALLS_PASSED" : "READY_FOR_VERIFICATION", "configured", ready,
                "providerConnectivity", failed ? "FAIL" : passed ? "PASS" : "NOT_CHECKED", "checks", checks,
                "providerObservations", Map.of("ASR",asrHealth,"TTS",ttsHealth));
    }

    private static Map<String, Object> check(String id, String label, boolean ready, String reason) {
        return Map.of("id", id, "label", label, "ready", ready, "reasonCode", ready ? "AVAILABLE" : reason);
    }

    private static String providerReason(Object candidate, String fallback) {
        if (candidate instanceof ProviderAdapter provider
                && provider.reasonCode() != null && !provider.reasonCode().isBlank()) {
            return provider.reasonCode();
        }
        return fallback;
    }
}
