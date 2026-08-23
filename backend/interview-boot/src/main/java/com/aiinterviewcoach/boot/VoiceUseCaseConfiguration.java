package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.outbound.voice.InMemoryVoiceSessionTicketAdapter;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.interview.internal.DefaultConfirmedTranscriptAnswerPort;
import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.voice.CheckVoicePreflight;
import com.aiinterviewcoach.application.voice.ConfirmTranscript;
import com.aiinterviewcoach.application.voice.DeleteAudioArtifact;
import com.aiinterviewcoach.application.voice.GetAudioArtifactStatus;
import com.aiinterviewcoach.application.voice.GetTranscript;
import com.aiinterviewcoach.application.voice.OpenVoiceSession;
import com.aiinterviewcoach.application.voice.internal.DefaultCheckVoicePreflight;
import com.aiinterviewcoach.application.voice.internal.DefaultConfirmTranscript;
import com.aiinterviewcoach.application.voice.internal.DefaultDeleteAudioArtifact;
import com.aiinterviewcoach.application.voice.internal.DefaultGetAudioArtifactStatus;
import com.aiinterviewcoach.application.voice.internal.DefaultGetTranscript;
import com.aiinterviewcoach.application.voice.internal.DefaultOpenVoiceSession;
import com.aiinterviewcoach.application.voice.port.ConfirmedTranscriptAnswerPort;
import com.aiinterviewcoach.application.voice.port.ContentDigestPort;
import com.aiinterviewcoach.application.voice.port.InterviewVoiceAccessPort;
import com.aiinterviewcoach.application.voice.port.VoiceCapabilityPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;
import com.aiinterviewcoach.application.governance.port.ConsentQueryPort;
import com.aiinterviewcoach.application.governance.internal.DefaultConsentQuery;
import com.aiinterviewcoach.application.governance.GrantConsent;
import com.aiinterviewcoach.application.governance.internal.DefaultGrantConsent;
import com.aiinterviewcoach.application.governance.port.ConsentRepository;
import com.aiinterviewcoach.application.integration.port.ObjectStoragePort;
import com.aiinterviewcoach.domain.interview.SessionState;
import com.aiinterviewcoach.domain.interview.TurnState;
import com.aiinterviewcoach.domain.platform.AggregateVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Voice 纵切的运行时组合根；外部 Provider 调用仍由 foundation safety gate 控制。 */
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
                    boolean currentTurn = !session.turns().isEmpty()
                            && session.turns().getLast().id().equals(turnId)
                            && session.turns().getLast().state() == TurnState.QUESTION_COMMITTED;
                    boolean allowed = owner && currentTurn && session.state() == SessionState.IN_PROGRESS;
                    return new InterviewVoiceAccessPort.Access(allowed, session.state().name(),
                            session.version(), allowed ? Optional.empty() : Optional.of("VOICE_TURN_NOT_ACTIVE"));
                })
                .orElseGet(() -> new InterviewVoiceAccessPort.Access(false, "NOT_FOUND",
                        AggregateVersion.initial(), Optional.of("VOICE_TURN_NOT_FOUND")));
    }

    @Bean
    VoiceCapabilityPort voiceCapabilityPort() {
        return (tenantId, userId) -> new VoiceCapabilityPort.Capability(true,
                List.of("audio/webm;codecs=opus", "audio/ogg;codecs=opus", "audio/wav"),
                120, 12L * 1024 * 1024, 8, 512L * 1024, 4_000,
                Optional.empty());
    }

    @Bean
    InMemoryVoiceSessionTicketAdapter voiceSessionTicketAdapter(Clock clock) {
        return new InMemoryVoiceSessionTicketAdapter(clock);
    }

    @Bean
    CheckVoicePreflight checkVoicePreflight(ConsentQueryPort consents, VoiceCapabilityPort capability,
                                            InterviewVoiceAccessPort access) {
        return new DefaultCheckVoicePreflight(consents, capability, access);
    }

    @Bean
    OpenVoiceSession openVoiceSession(VoiceRepository repository, InterviewVoiceAccessPort access,
                                      ConsentQueryPort consents, VoiceCapabilityPort capability,
                                      InMemoryVoiceSessionTicketAdapter tickets, IdGeneratorPort ids,
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
}
