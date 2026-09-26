package com.ruoyi.interview.controller.websocket;

import com.ruoyi.interview.application.agent.port.InvocationContext;
import com.ruoyi.interview.application.agent.port.SpeechToTextPort;
import com.ruoyi.interview.application.agent.port.TextToSpeechPort;
import com.ruoyi.interview.application.integration.port.ObjectStoragePort;
import com.ruoyi.interview.application.platform.port.ClockPort;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.voice.port.ContentDigestPort;
import com.ruoyi.interview.application.voice.port.VoiceRepository;
import com.ruoyi.interview.application.voice.port.VoiceSessionTicketPort;
import com.ruoyi.interview.domain.platform.ArtifactRef;
import com.ruoyi.interview.domain.platform.CorrelationId;
import com.ruoyi.interview.domain.platform.CostBudget;
import com.ruoyi.interview.domain.platform.DataClassification;
import com.ruoyi.interview.domain.platform.EventContext;
import com.ruoyi.interview.domain.platform.Money;
import com.ruoyi.interview.domain.platform.ProviderConfigRef;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TimeBudget;
import com.ruoyi.interview.domain.voice.Transcript;
import com.ruoyi.interview.domain.voice.TranscriptSource;
import com.ruoyi.interview.domain.voice.TranscriptVersion;
import com.ruoyi.interview.domain.voice.AudioArtifact;
import com.ruoyi.interview.domain.voice.AudioPurpose;
import com.ruoyi.interview.infrastructure.provider.ProviderAdapter;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** WebSocket transport 与 Voice domain/ASR 之间的窄编排层。 */
public final class VoiceCaptureCoordinator {
    private static final long MAX_CHUNK_BYTES = 512L * 1024;
    private static final long MAX_TOTAL_BYTES = 12L * 1024 * 1024;
    private static final long MAX_TOTAL_DURATION_MILLIS = 120_000L;
    private final VoiceRepository repository;
    private final ObjectStoragePort storage;
    private final SpeechToTextPort speechToText;
    private final TextToSpeechPort textToSpeech;
    private final IdGeneratorPort ids;
    private final ContentDigestPort digest;
    private final DomainEventPort events;
    private final TransactionPort transaction;
    private final ClockPort clock;
    private final String providerAlias;
    private final String modelAlias;
    private final String language;
    private final String ttsModelAlias;
    private final String ttsVoice;
    private final String ttsLanguage;
    private final String ttsCodec;
    private final int ttsSampleRate;

    public VoiceCaptureCoordinator(VoiceRepository repository, ObjectStoragePort storage,
                                   SpeechToTextPort speechToText, TextToSpeechPort textToSpeech,
                                   IdGeneratorPort ids,
                                   ContentDigestPort digest, DomainEventPort events,
                                   TransactionPort transaction, ClockPort clock,
                                   String providerAlias, String modelAlias, String language,
                                   String ttsModelAlias, String ttsVoice, String ttsLanguage,
                                   String ttsCodec, int ttsSampleRate) {
        this.repository = repository; this.storage = storage; this.speechToText = speechToText;
        this.textToSpeech = textToSpeech;
        this.ids = ids; this.digest = digest; this.events = events; this.transaction = transaction;
        this.clock = clock; this.providerAlias = providerAlias; this.modelAlias = modelAlias;
        this.language = language;
        this.ttsModelAlias = ttsModelAlias; this.ttsVoice = ttsVoice;
        this.ttsLanguage = ttsLanguage; this.ttsCodec = ttsCodec; this.ttsSampleRate = ttsSampleRate;
    }

    public Capture begin(VoiceSessionTicketPort.ConsumedTicket ticket, int sampleRate,
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
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("音频分片大小超出服务端限制");
        }
        if (durationMillis <= 0 || capture.bytes + bytes.length > MAX_TOTAL_BYTES
                || capture.durationMillis + durationMillis > MAX_TOTAL_DURATION_MILLIS) {
            throw new IllegalArgumentException("音频总大小或时长超出服务端限制");
        }
        storage.writeChunk(capture.handle(), new ObjectStoragePort.StorageChunk(
                capture.nextStorageSequence(), bytes, false));
        recordClientSequence(capture.ticket(), sequence);
        capture.add(bytes.length, durationMillis);
    }

    public void recordClientSequence(VoiceSessionTicketPort.ConsumedTicket ticket, long sequence) {
        var request = ticket.request();
        transaction.required(() -> {
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.acceptClientSequence(sequence, execution.version());
            repository.saveExecution(execution);
        });
    }

    public long nextServerSequence(VoiceSessionTicketPort.ConsumedTicket ticket) {
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
            repository.saveArtifact(artifact);
            artifact.beginTranscription(artifact.version(), eventContext);
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.markTranscribing(execution.version());
            repository.saveArtifact(artifact); repository.saveExecution(execution);
            events.append(artifact.pullDomainEvents());
        });

        ResourceId invocationId = ids.nextResourceId();
        final SpeechToTextPort.Result asr;
        try {
            asr = speechToText.transcribe(new SpeechToTextPort.Request(
                            new ArtifactRef(request.artifactId(), "ANSWER_TRANSCRIPTION",
                                    DataClassification.HIGHLY_SENSITIVE, now.plus(Duration.ofHours(24))),
                            language, java.util.List.of(), "UTF16",
                            new ProviderConfigRef("ASR", providerAlias, modelAlias, 1)),
                    new InvocationContext(request.tenantId(), invocationId, correlationId,
                            new TimeBudget(Duration.ofSeconds(45)),
                            new CostBudget(new Money(BigDecimal.ONE, "CNY"))));
        } catch (com.ruoyi.interview.infrastructure.AdapterUnavailableException exception) {
            String code = speechProviderReason("ASR_NOT_CONFIGURED");
            transaction.required(() -> failTranscription(request, invocationId, code, eventContext));
            return new Failure(code);
        } catch (RuntimeException exception) {
            transaction.required(() -> failTranscription(request, invocationId,
                    "PROVIDER_UNAVAILABLE", eventContext));
            return new Failure("PROVIDER_UNAVAILABLE");
        }
        if (asr instanceof SpeechToTextPort.Failure failure) {
            String code = stableReasonCode(failure.failure().errorClass(), "PROVIDER_BAD_RESPONSE");
            transaction.required(() -> failTranscription(request, invocationId, code, eventContext));
            return new Failure(code);
        }
        if (!(asr instanceof SpeechToTextPort.Success success)) {
            transaction.required(() -> failTranscription(request, invocationId,
                    "PROVIDER_BAD_RESPONSE", eventContext));
            return new Failure("PROVIDER_BAD_RESPONSE");
        }
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
            if (artifact.state() == com.ruoyi.interview.domain.voice.AudioArtifactState.UPLOADING) {
                artifact.failUpload(reasonCode, artifact.version(), new EventContext(correlationId, clock.now()));
                repository.saveArtifact(artifact); events.append(artifact.pullDomainEvents());
            }
            var execution = repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .orElseThrow();
            execution.degrade(reasonCode, execution.version()); repository.saveExecution(execution);
        });
    }

    /**
     * 拒绝不支持的 resume cursor 时清理本次已创建但尚未开始上传的资源，
     * 并将语音执行降级为可继续使用文字回答的状态。这里不尝试重放任何
     * WebSocket 事件，已确认事实仍由 REST snapshot 恢复。
     */
    public void rejectResume(VoiceSessionTicketPort.ConsumedTicket ticket, String reasonCode,
                             CorrelationId correlationId) {
        var request = ticket.request();
        var context = new EventContext(correlationId, clock.now());
        transaction.required(() -> {
            repository.findArtifact(request.tenantId(), request.artifactId()).ifPresent(artifact -> {
                if (artifact.state() != com.ruoyi.interview.domain.voice.AudioArtifactState.DELETED
                        && artifact.state() != com.ruoyi.interview.domain.voice.AudioArtifactState.DELETE_QUEUED) {
                    artifact.queueDeletion(artifact.version(), context);
                    repository.saveArtifact(artifact);
                    events.append(artifact.pullDomainEvents());
                }
            });
            repository.findExecution(request.tenantId(), request.sessionId(), request.turnId())
                    .ifPresent(execution -> {
                        if (execution.state() != com.ruoyi.interview.domain.voice.VoiceTurnState.CANCELLED
                                && execution.state() != com.ruoyi.interview.domain.voice.VoiceTurnState.DEGRADED) {
                            execution.degrade(reasonCode, execution.version());
                            repository.saveExecution(execution);
                        }
                    });
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

    public boolean ttsAvailable() {
        return textToSpeech instanceof ProviderAdapter adapter && adapter.available()
                && ttsModelAlias != null && !ttsModelAlias.isBlank()
                && ttsVoice != null && !ttsVoice.isBlank()
                && ttsCodec != null && !ttsCodec.isBlank() && ttsSampleRate > 0;
    }

    public String ttsCodec() {
        return ttsCodec;
    }

    public int ttsSampleRate() {
        return ttsSampleRate;
    }

    public TtsResult synthesizeNextQuestion(
            VoiceSessionTicketPort.ConsumedTicket ticket,
            String questionText,
            TtsChunkConsumer consumer,
            BooleanSupplier cancelled,
            CorrelationId correlationId
    ) {
        var request = ticket.request();
        if (!ttsAvailable()) {
            degradeTts(request.tenantId(), request.sessionId(), request.turnId(),
                    "TTS_NOT_CONFIGURED");
            return new TtsFailure("TTS_NOT_CONFIGURED");
        }
        var input = repository.findArtifact(request.tenantId(), request.artifactId()).orElseThrow();
        ResourceId outputId = ids.nextResourceId();
        ResourceId invocationId = ids.nextResourceId();
        var now = clock.now();
        var context = new EventContext(correlationId, now);
        AudioArtifact output = AudioArtifact.create(outputId, request.tenantId(), request.sessionId(),
                request.turnId(), AudioPurpose.TTS_PLAYBACK, input.consentRecordId(),
                now.plus(Duration.ofHours(24)), context);
        output.beginSynthesis(ttsCodec, ttsSampleRate, 1, output.version(), context);
        var upload = storage.beginUpload(new ObjectStoragePort.BeginUpload(
                request.tenantId(), outputId, ttsCodec, 20L * 1024 * 1024, output.expiresAt()));
        transaction.required(() -> {
            var execution = repository.findExecution(
                    request.tenantId(), request.sessionId(), request.turnId()).orElseThrow();
            execution.markSpeaking(outputId, execution.version());
            repository.saveArtifact(output); repository.saveExecution(execution);
            events.append(output.pullDomainEvents());
        });
        try {
            var result = textToSpeech.synthesize(new TextToSpeechPort.Request(
                            questionText, ttsLanguage, ttsVoice, ttsCodec,
                            new ProviderConfigRef("TTS", providerAlias, ttsModelAlias, 1)),
                    (sequence, bytes, end) -> {
                        if (cancelled.getAsBoolean()) throw new TtsCancelledException();
                        storage.writeChunk(upload, new ObjectStoragePort.StorageChunk(sequence, bytes, end));
                        consumer.accept(sequence, bytes, end);
                    }, new InvocationContext(request.tenantId(), invocationId, correlationId,
                            new TimeBudget(Duration.ofSeconds(45)),
                            new CostBudget(new Money(BigDecimal.ONE, "CNY"))));
            if (cancelled.getAsBoolean()) {
                return cancelSynthesis(request, output, upload, context);
            }
            if (result instanceof TextToSpeechPort.Failure failure) {
                storage.abortUpload(upload, failure.failure().errorClass());
                return failSynthesis(request, output, invocationId,
                        failure.failure().errorClass(), context);
            }
            var success = (TextToSpeechPort.Success) result;
            var stored = storage.completeUpload(upload);
            if (stored.bytes() != success.bytes()) {
                return failSynthesis(request, output, invocationId, "TTS_SIZE_MISMATCH", context);
            }
            transaction.required(() -> {
                var current = repository.findArtifact(request.tenantId(), outputId).orElseThrow();
                current.markSynthesized(stored.objectRef(), stored.bytes(), success.durationMillis(),
                        stored.contentHash(), invocationId, current.version(), context);
                var execution = repository.findExecution(
                        request.tenantId(), request.sessionId(), request.turnId()).orElseThrow();
                execution.finishPlayback(execution.version());
                repository.saveArtifact(current); repository.saveExecution(execution);
                events.append(current.pullDomainEvents());
            });
            return new TtsSuccess(outputId);
        } catch (TtsCancelledException exception) {
            return cancelSynthesis(request, output, upload, context);
        } catch (RuntimeException exception) {
            storage.abortUpload(upload, "TTS_PIPELINE_FAILED");
            return failSynthesis(request, output, invocationId, "TTS_PIPELINE_FAILED", context);
        }
    }

    private TtsResult cancelSynthesis(VoiceSessionTicketPort.IssueRequest request, AudioArtifact output,
                                      ObjectStoragePort.UploadHandle upload, EventContext context) {
        storage.abortUpload(upload, "TTS_CANCELLED");
        transaction.required(() -> {
            var current = repository.findArtifact(request.tenantId(), output.id()).orElseThrow();
            current.queueDeletion(current.version(), context);
            var execution = repository.findExecution(
                    request.tenantId(), request.sessionId(), request.turnId()).orElseThrow();
            execution.finishPlayback(execution.version());
            repository.saveArtifact(current); repository.saveExecution(execution);
            events.append(current.pullDomainEvents());
        });
        return new TtsCancelled();
    }

    private TtsResult failSynthesis(VoiceSessionTicketPort.IssueRequest request, AudioArtifact output,
                                    ResourceId invocationId, String code, EventContext context) {
        transaction.required(() -> {
            var current = repository.findArtifact(request.tenantId(), output.id()).orElseThrow();
            current.failSynthesis(code, invocationId, current.version(), context);
            var execution = repository.findExecution(
                    request.tenantId(), request.sessionId(), request.turnId()).orElseThrow();
            execution.degrade(code, execution.version());
            repository.saveArtifact(current); repository.saveExecution(execution);
            events.append(current.pullDomainEvents());
        });
        return new TtsFailure(code);
    }

    public void finishWithoutTts(com.ruoyi.interview.domain.platform.TenantId tenantId,
                                 ResourceId sessionId, ResourceId turnId) {
        transaction.required(() -> {
            var execution = repository.findExecution(tenantId, sessionId, turnId).orElseThrow();
            if (execution.state() == com.ruoyi.interview.domain.voice.VoiceTurnState.THINKING) {
                execution.finishThinking(execution.version());
                repository.saveExecution(execution);
            }
        });
    }

    public void degradeTts(com.ruoyi.interview.domain.platform.TenantId tenantId,
                           ResourceId sessionId, ResourceId turnId, String reasonCode) {
        transaction.required(() -> {
            var execution = repository.findExecution(tenantId, sessionId, turnId).orElseThrow();
            if (execution.state() == com.ruoyi.interview.domain.voice.VoiceTurnState.THINKING
                    || execution.state() == com.ruoyi.interview.domain.voice.VoiceTurnState.SPEAKING) {
                execution.degrade(reasonCode, execution.version());
                repository.saveExecution(execution);
            }
        });
    }

    public static final class Capture {
        private final VoiceSessionTicketPort.ConsumedTicket ticket;
        private final ObjectStoragePort.UploadHandle handle;
        private long bytes; private long durationMillis; private long storageSequence;
        Capture(VoiceSessionTicketPort.ConsumedTicket ticket, ObjectStoragePort.UploadHandle handle) {
            this.ticket = ticket; this.handle = handle;
        }
        void add(long bytes, long duration) { this.bytes += bytes; this.durationMillis += duration; }
        long nextStorageSequence() { return ++storageSequence; }
        public VoiceSessionTicketPort.ConsumedTicket ticket() { return ticket; }
        public ObjectStoragePort.UploadHandle handle() { return handle; }
    }
    public sealed interface Result permits Success, Failure { }
    public record Success(ResourceId transcriptId, ResourceId transcriptVersionId,
                          ResourceId artifactId, String text, String language, String offsetUnit,
                          java.util.List<com.ruoyi.interview.domain.voice.ConfidenceSpan> spans,
                          long transcriptVersion) implements Result { }
    public record Failure(String reasonCode) implements Result { }
    @FunctionalInterface public interface TtsChunkConsumer {
        void accept(long sequence, byte[] bytes, boolean endOfOutput);
    }
    public sealed interface TtsResult permits TtsSuccess, TtsFailure, TtsCancelled { }
    public record TtsSuccess(ResourceId outputArtifactId) implements TtsResult { }
    public record TtsFailure(String reasonCode) implements TtsResult { }
    public record TtsCancelled() implements TtsResult { }
    private static final class TtsCancelledException extends RuntimeException { }

    private String speechProviderReason(String fallback) {
        if (speechToText instanceof ProviderAdapter adapter
                && adapter.reasonCode() != null && !adapter.reasonCode().isBlank()
                && !"AVAILABLE".equals(adapter.reasonCode())) {
            return stableReasonCode(adapter.reasonCode(), fallback);
        }
        return fallback;
    }

    private static String stableReasonCode(String value, String fallback) {
        if (value != null && value.matches("[A-Z][A-Z0-9_.-]{0,95}")) return value;
        return fallback;
    }
}

