package com.ruoyi.interview.configuration;

import com.ruoyi.interview.controller.websocket.VoiceCaptureCoordinator;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.interview.internal.DefaultConfirmedTranscriptAnswerPort;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.JobPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.voice.CheckVoicePreflight;
import com.ruoyi.interview.application.voice.ConfirmTranscript;
import com.ruoyi.interview.application.voice.DeleteAudioArtifact;
import com.ruoyi.interview.application.voice.GetAudioArtifactStatus;
import com.ruoyi.interview.application.voice.GetTranscript;
import com.ruoyi.interview.application.voice.OpenVoiceSession;
import com.ruoyi.interview.application.voice.internal.DefaultCheckVoicePreflight;
import com.ruoyi.interview.application.voice.internal.DefaultConfirmTranscript;
import com.ruoyi.interview.application.voice.internal.DefaultDeleteAudioArtifact;
import com.ruoyi.interview.application.voice.internal.DefaultGetAudioArtifactStatus;
import com.ruoyi.interview.application.voice.internal.DefaultGetTranscript;
import com.ruoyi.interview.application.voice.internal.DefaultOpenVoiceSession;
import com.ruoyi.interview.application.voice.port.ConfirmedTranscriptAnswerPort;
import com.ruoyi.interview.application.voice.port.ContentDigestPort;
import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.application.agent.port.TextToSpeechPort;
import com.ruoyi.interview.application.voice.port.InterviewVoiceAccessPort;
import com.ruoyi.interview.application.voice.port.VoiceCapabilityPort;
import com.ruoyi.interview.application.voice.port.VoiceRepository;
import com.ruoyi.interview.application.governance.port.ConsentQueryPort;
import com.ruoyi.interview.application.governance.internal.DefaultConsentQuery;
import com.ruoyi.interview.application.governance.GrantConsent;
import com.ruoyi.interview.application.governance.internal.DefaultGrantConsent;
import com.ruoyi.interview.application.governance.port.ConsentRepository;
import com.ruoyi.interview.application.integration.port.ObjectStoragePort;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.persistence.shared.UnavailableSensitiveEnvelopeCipher;
import com.ruoyi.interview.infrastructure.provider.ProviderAdapter;
import com.ruoyi.interview.infrastructure.storage.ObjectStorageAdapter;
import com.ruoyi.interview.domain.interview.SessionState;
import com.ruoyi.interview.domain.interview.TurnState;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.configuration.properties.ProviderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Voice 纵切的运行时组合根；外部 Provider 调用仍由 foundation safety gate 控制。 */
@InterviewEnabled
@Configuration
public class VoiceUseCaseConfiguration {

    @Bean
    ConsentQueryPort consentQueryPort(ConsentRepository repository) {
        return new DefaultConsentQuery(repository);
    }

    @Bean
    GrantConsent grantConsent(ActivePrincipalGuard principal, ConsentRepository repository,
                              IdGeneratorPort ids, IdempotencyGuard idempotency,
                              TransactionPort transaction) {
        return new DefaultGrantConsent(principal, repository, ids, idempotency, transaction);
    }

    @Bean
    InterviewVoiceAccessPort interviewVoiceAccessPort(InterviewRepository repository) {
        return (tenantId, userId, sessionId, turnId) -> repository.findSession(tenantId, sessionId)
                .map(session -> {
                    boolean owner = session.userId().equals(userId);
                    var turns = session.turns();
                    var current = turns.isEmpty() ? null : turns.get(turns.size() - 1);
                    boolean currentTurn = current != null
                            && current.id().equals(turnId)
                            && current.state() == TurnState.QUESTION_COMMITTED;
                    boolean allowed = owner && currentTurn && session.state() == SessionState.IN_PROGRESS;
                    return new InterviewVoiceAccessPort.Access(allowed, session.state().name(),
                            session.version(), allowed ? Optional.empty() : Optional.of("VOICE_TURN_NOT_ACTIVE"));
                })
                .orElseGet(() -> new InterviewVoiceAccessPort.Access(false, "NOT_FOUND",
                        AggregateVersion.initial(), Optional.of("VOICE_TURN_NOT_FOUND")));
    }

    @Bean
    VoiceCapabilityPort voiceCapabilityPort(ObjectStoragePort storage, SpeechToTextPort speechToText,
                                             SensitiveEnvelopeCipher cipher) {
        return (tenantId, userId) -> {
            Optional<String> reason = unavailableReason(storage, speechToText, cipher);
            return new VoiceCapabilityPort.Capability(reason.isEmpty(),
                    // 火山 ASR 不接受 WebM 容器；Portal 会在上传前统一封装为 PCM WAV。
                    List.of("audio/ogg;codecs=opus", "audio/wav"),
                    120, 12L * 1024 * 1024, 8, 512L * 1024, 4_000, reason);
        };
    }

    @Bean
    VoiceCaptureCoordinator voiceCaptureCoordinator(
            VoiceRepository repository,
            ObjectStoragePort storage,
            SpeechToTextPort speechToText,
            TextToSpeechPort textToSpeech,
            IdGeneratorPort ids,
            ContentDigestPort digest,
            DomainEventPort events,
            TransactionPort transaction,
            com.ruoyi.interview.application.platform.port.ClockPort clock,
            ProviderProperties providers) {
        ProviderProperties.Asr asr = providers.getVolcengineSpeech().getAsr();
        ProviderProperties.Tts tts = providers.getVolcengineSpeech().getTts();
        return new VoiceCaptureCoordinator(repository, storage, speechToText, textToSpeech, ids, digest,
                events, transaction, clock, "VOLCENGINE", asr.getModel(),
                asr.getLanguage() == null || asr.getLanguage().isBlank() ? "zh-CN" : asr.getLanguage(),
                tts.getModel(), tts.getVoice(),
                tts.getLanguage() == null || tts.getLanguage().isBlank() ? "zh-CN" : tts.getLanguage(),
                tts.getAudioFormat() == null || tts.getAudioFormat().isBlank()
                        ? "ogg_opus" : tts.getAudioFormat(), tts.getSampleRate());
    }

    @Bean
    CheckVoicePreflight checkVoicePreflight(ConsentQueryPort consents, VoiceCapabilityPort capability,
                                            InterviewVoiceAccessPort access) {
        return new DefaultCheckVoicePreflight(consents, capability, access);
    }

    @Bean
    OpenVoiceSession openVoiceSession(VoiceRepository repository, InterviewVoiceAccessPort access,
                                      ConsentQueryPort consents, VoiceCapabilityPort capability,
                                      com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort tickets, IdGeneratorPort ids,
                                      DomainEventPort events, TransactionPort transaction) {
        return new DefaultOpenVoiceSession(repository, access, consents, capability, tickets, ids,
                events, transaction, Duration.ofHours(24), Duration.ofMinutes(5));
    }

    @Bean
    GetTranscript getTranscript(VoiceRepository repository, InterviewVoiceAccessPort access) {
        return new DefaultGetTranscript(repository, access);
    }

    @Bean
    GetAudioArtifactStatus getAudioArtifactStatus(VoiceRepository repository,
                                                   InterviewVoiceAccessPort access) {
        return new DefaultGetAudioArtifactStatus(repository, access);
    }

    @Bean
    ConfirmedTranscriptAnswerPort confirmedTranscriptAnswerPort(InterviewRepository repository,
                                                                JobPort jobs,
                                                                ActivePrincipalGuard principal,
                                                                IdGeneratorPort ids,
                                                                DomainEventPort events) {
        return new DefaultConfirmedTranscriptAnswerPort(repository, jobs, principal, ids, events);
    }

    @Bean
    ConfirmTranscript confirmTranscript(VoiceRepository repository, ActivePrincipalGuard principal,
                                        IdGeneratorPort ids, ContentDigestPort digest,
                                        InterviewVoiceAccessPort access,
                                        ConfirmedTranscriptAnswerPort answers,
                                        IdempotencyGuard idempotency, DomainEventPort events,
                                        TransactionPort transaction) {
        return new DefaultConfirmTranscript(repository, principal, ids, digest, access, answers,
                idempotency, events, transaction);
    }

    @Bean
    DeleteAudioArtifact deleteAudioArtifact(VoiceRepository repository, ObjectStoragePort storage,
                                            DomainEventPort events, TransactionPort transaction) {
        return new DefaultDeleteAudioArtifact(repository, storage, events, transaction);
    }

    private static Optional<String> unavailableReason(
            ObjectStoragePort storage,
            SpeechToTextPort speechToText,
            SensitiveEnvelopeCipher cipher
    ) {
        if (!(storage instanceof ObjectStorageAdapter storageAdapter) || !storageAdapter.available()) {
            return Optional.of("OBJECT_STORAGE_NOT_READY");
        }
        if (!(speechToText instanceof ProviderAdapter providerAdapter) || !providerAdapter.available()) {
            if (speechToText instanceof ProviderAdapter unavailableProvider
                    && unavailableProvider.reasonCode() != null
                    && !unavailableProvider.reasonCode().isBlank()) {
                return Optional.of(unavailableProvider.reasonCode());
            }
            return Optional.of("ASR_NOT_CONFIGURED");
        }
        if (cipher instanceof UnavailableSensitiveEnvelopeCipher) {
            return Optional.of("SENSITIVE_ENVELOPE_NOT_READY");
        }
        return Optional.empty();
    }
}

