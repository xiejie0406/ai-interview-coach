package com.aiinterviewcoach.adapters.inbound.websocket;

import com.aiinterviewcoach.adapters.outbound.voice.InMemoryVoiceSessionTicketAdapter;
import com.aiinterviewcoach.application.agent.port.InvocationContext;
import com.aiinterviewcoach.application.agent.port.SpeechToTextPort;
import com.aiinterviewcoach.application.integration.port.ObjectStoragePort;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.voice.port.ContentDigestPort;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;
import com.aiinterviewcoach.application.voice.port.VoiceSessionTicketPort;
import com.aiinterviewcoach.domain.platform.ArtifactRef;
import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.CostBudget;
import com.aiinterviewcoach.domain.platform.DataClassification;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.platform.Money;
import com.aiinterviewcoach.domain.platform.ProviderConfigRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TimeBudget;
import com.aiinterviewcoach.domain.voice.Transcript;
import com.aiinterviewcoach.domain.voice.TranscriptSource;
import com.aiinterviewcoach.domain.voice.TranscriptVersion;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/** WebSocket transport 与 Voice domain/ASR 之间的窄编排层。 */
public final class VoiceCaptureCoordinator {
    private final VoiceRepository repository;
    private final ObjectStoragePort storage;
    private final SpeechToTextPort speechToText;
    private final IdGeneratorPort ids;
    private final ContentDigestPort digest;
    private final DomainEventPort events;
    private final TransactionPort transaction;
    private final ClockPort clock;
    private final String providerAlias;
    private final String modelAlias;
    private final String language;

    public VoiceCaptureCoordinator(VoiceRepository repository, ObjectStoragePort storage,
                                   SpeechToTextPort speechToText, IdGeneratorPort ids,
                                   ContentDigestPort digest, DomainEventPort events,
                                   TransactionPort transaction, ClockPort clock,
                                   String providerAlias, String modelAlias, String language) {
        this.repository = repository; this.storage = storage; this.speechToText = speechToText;
        this.ids = ids; this.digest = digest; this.events = events; this.transaction = transaction;
        this.clock = clock; this.providerAlias = providerAlias; this.modelAlias = modelAlias;
        this.language = language;
    }

    public Capture begin(InMemoryVoiceSessionTicketAdapter.Ticket ticket, int sampleRate,
                         int channelCount, CorrelationId correlationId) {
        var request = ticket.request();
        var artifact = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
        var handle = storage.beginUpload(new ObjectStoragePort.BeginUpload(request.tenantId(),
                request.artifactId(), request.codec(), 12L * 1024 * 1024, artifact.expiresAt()));
        try {
            transaction.required(() -> {
                var current = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
                current.beginUpload(request.codec(), sampleRate, channelCount, current.version(),
                        new EventContext(correlationId, clock.now()));
                repository.saveArtifact(current);
                events.append(current.pullDomainEvents());
            });
            return new Capture(ticket, handle);
        } catch (RuntimeException exception) {
            storage.abortUpload(handle, "DOMAIN_BEGIN_FAILED");
            throw exception;
        }
    }

    public void accept(Capture capture, long sequence, byte[] bytes, long durationMillis,
                       CorrelationId correlationId) {
        storage.writeChunk(capture.handle(), new ObjectStoragePort.StorageChunk(
                capture.nextStorageSequence(), bytes, false));
        recordClientSequence(capture.ticket(), sequence);
        capture.add(bytes.length, durationMillis);
    }

    public void recordClientSequence(InMemoryVoiceSessionTicketAdapter.Ticket ticket, long sequence) {
        var request = ticket.request();
        transaction.required(() -> {
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.acceptClientSequence(sequence, execution.version());
            repository.saveExecution(execution);
        });
    }

    public long nextServerSequence(InMemoryVoiceSessionTicketAdapter.Ticket ticket) {
        var request = ticket.request();
        return transaction.required(() -> {
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            long next = execution.lastServerSequence() + 1;
            execution.emitServerSequence(next, execution.version());
            repository.saveExecution(execution);
            return next;
        });
    }

    public Result complete(Capture capture, long declaredBytes, long declaredDuration,
                           CorrelationId correlationId) {
        if (declaredBytes != capture.bytes || declaredDuration != capture.durationMillis
                || declaredBytes <= 0 || declaredDuration <= 0) {
            abort(capture, "AUDIO_TOTAL_MISMATCH", correlationId);
            return new Failure("AUDIO_TOTAL_MISMATCH");
        }
        var stored = storage.completeUpload(capture.handle());
        var request = capture.ticket().request();
        var now = clock.now();
        var eventContext = new EventContext(correlationId, now);
        transaction.required(() -> {
            var artifact = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
            artifact.markUploaded(stored.objectRef(), stored.bytes(), declaredDuration,
                    stored.contentHash(), artifact.version(), eventContext);
            artifact.beginTranscription(artifact.version(), eventContext);
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.markTranscribing(execution.version());
            repository.saveArtifact(artifact); repository.saveExecution(execution);
            events.append(artifact.pullDomainEvents());
        });

        ResourceId invocationId = ids.nextResourceId();
        var asr = speechToText.transcribe(new SpeechToTextPort.Request(
                        new ArtifactRef(request.artifactId(), "ANSWER_TRANSCRIPTION",
                                DataClassification.HIGHLY_SENSITIVE, now.plus(Duration.ofHours(24))),
                        language, java.util.List.of(), "UTF16",
                        new ProviderConfigRef("ASR", providerAlias, modelAlias, 1)),
                new InvocationContext(request.tenantId(), invocationId, correlationId,
                        new TimeBudget(Duration.ofSeconds(45)),
                        new CostBudget(new Money(BigDecimal.ONE, "CNY"))));
        if (asr instanceof SpeechToTextPort.Failure failure) {
            String code = failure.failure().errorClass();
            transaction.required(() -> failTranscription(request, invocationId, code, eventContext));
            return new Failure(code);
        }
        SpeechToTextPort.Success success = (SpeechToTextPort.Success) asr;
        return transaction.required(() -> {
            var artifact = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
            artifact.markTranscribed(invocationId, artifact.version(), eventContext);
            ResourceId transcriptId = ids.nextResourceId();
            Transcript transcript = Transcript.open(transcriptId, artifact, eventContext);
            TranscriptVersion version = new TranscriptVersion(ids.nextResourceId(), request.tenantId(),
                    transcriptId, 1, TranscriptSource.ASR, success.transcriptText(),
                    digest.digest(success.transcriptText()), success.language(), success.offsetUnit(),
                    success.lowConfidenceSpans(), Optional.of(invocationId), Optional.empty(),
                    Optional.empty(), now);
            transcript.appendAsrFinal(version, transcript.version(), eventContext);
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.markConfirming(transcriptId, execution.version());
            repository.saveArtifact(artifact); repository.saveTranscript(transcript);
            repository.saveExecution(execution);
            events.append(artifact.pullDomainEvents()); events.append(transcript.pullDomainEvents());
            return new Success(transcriptId, version.id(), request.artifactId(), success.transcriptText(),
                    success.language(), success.offsetUnit(), success.lowConfidenceSpans(), transcript.version().value());
        });
    }

    public void abort(Capture capture, String reasonCode, CorrelationId correlationId) {
        storage.abortUpload(capture.handle(), reasonCode);
        var request = capture.ticket().request();
        transaction.required(() -> {
            var artifact = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
            if (artifact.state() == com.aiinterviewcoach.domain.voice.AudioArtifactState.UPLOADING) {
                artifact.failUpload(reasonCode, artifact.version(), new EventContext(correlationId, clock.now()));
                repository.saveArtifact(artifact); events.append(artifact.pullDomainEvents());
            }
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.degrade(reasonCode, execution.version()); repository.saveExecution(execution);
        });
    }

    private void failTranscription(VoiceSessionTicketPort.IssueRequest request, ResourceId invocationId,
                                   String code, EventContext context) {
        var artifact = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
        artifact.failTranscription(code, invocationId, artifact.version(), context);
        var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                .orElseThrow();
        execution.degrade(code, execution.version());
        repository.saveArtifact(artifact); repository.saveExecution(execution);
        events.append(artifact.pullDomainEvents());
    }

    public static final class Capture {
        private final InMemoryVoiceSessionTicketAdapter.Ticket ticket;
        private final ObjectStoragePort.UploadHandle handle;
        private long bytes; private long durationMillis; private long storageSequence;
        Capture(InMemoryVoiceSessionTicketAdapter.Ticket ticket, ObjectStoragePort.UploadHandle handle) {
            this.ticket = ticket; this.handle = handle;
        }
        void add(long bytes, long duration) { this.bytes += bytes; this.durationMillis += duration; }
        long nextStorageSequence() { return ++storageSequence; }
        public InMemoryVoiceSessionTicketAdapter.Ticket ticket() { return ticket; }
        public ObjectStoragePort.UploadHandle handle() { return handle; }
    }
    public sealed interface Result permits Success, Failure { }
    public record Success(ResourceId transcriptId, ResourceId transcriptVersionId,
                          ResourceId artifactId, String text, String language, String offsetUnit,
                          java.util.List<com.aiinterviewcoach.domain.voice.ConfidenceSpan> spans,
                          long transcriptVersion) implements Result { }
    public record Failure(String reasonCode) implements Result { }
}
