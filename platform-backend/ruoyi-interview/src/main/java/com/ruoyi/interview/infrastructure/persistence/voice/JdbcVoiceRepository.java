package com.ruoyi.interview.infrastructure.persistence.voice;

import com.ruoyi.interview.infrastructure.persistence.shared.EncryptedEnvelope;
import com.ruoyi.interview.infrastructure.persistence.shared.JdbcPersistenceSupport;
import com.ruoyi.interview.infrastructure.persistence.shared.SensitiveEnvelopeCipher;
import com.ruoyi.interview.application.voice.port.VoiceRepository;
import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import com.ruoyi.interview.domain.platform.UserId;
import com.ruoyi.interview.domain.voice.AudioArtifact;
import com.ruoyi.interview.domain.voice.AudioArtifactState;
import com.ruoyi.interview.domain.voice.AudioPurpose;
import com.ruoyi.interview.domain.voice.ConfidenceSpan;
import com.ruoyi.interview.domain.voice.StorageObjectRef;
import com.ruoyi.interview.domain.voice.Transcript;
import com.ruoyi.interview.domain.voice.TranscriptSource;
import com.ruoyi.interview.domain.voice.TranscriptState;
import com.ruoyi.interview.domain.voice.TranscriptVersion;
import com.ruoyi.interview.domain.voice.VoiceTurnExecution;
import com.ruoyi.interview.domain.voice.VoiceTurnState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class JdbcVoiceRepository implements VoiceRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveEnvelopeCipher cipher;

    public JdbcVoiceRepository(NamedParameterJdbcTemplate jdbc, SensitiveEnvelopeCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public Optional<AudioArtifact> findArtifact(TenantId tenantId, ResourceId artifactId) {
        return jdbc.query("""
                select * from voice.audio_artifact
                 where tenant_id = :tenantId and artifact_id = :artifactId
                """, Map.of("tenantId", tenantId.value(), "artifactId", artifactId.value()),
                (row, rowNum) -> mapArtifact(row)).stream().findFirst();
    }

    @Override
    public Optional<Transcript> findTranscript(TenantId tenantId, ResourceId transcriptId) {
        return jdbc.query("""
                select * from voice.transcript
                 where tenant_id = :tenantId and transcript_id = :transcriptId
                """, Map.of("tenantId", tenantId.value(), "transcriptId", transcriptId.value()),
                (row, rowNum) -> mapTranscript(row)).stream().findFirst();
    }

    @Override
    public Optional<VoiceTurnExecution> findExecution(
            TenantId tenantId,
            ResourceId sessionId,
            ResourceId turnId
    ) {
        return jdbc.query("""
                select * from voice.turn_execution
                 where tenant_id = :tenantId and session_id = :sessionId and turn_id = :turnId
                """, Map.of("tenantId", tenantId.value(), "sessionId", sessionId.value(),
                        "turnId", turnId.value()), (row, rowNum) -> mapExecution(row)).stream().findFirst();
    }

    @Override
    public List<AudioArtifact> findDeletionDue(Instant now, int limit) {
        // Global worker scan is explicitly allowlisted by VoiceRepository; every returned row retains tenantId.
        return jdbc.query("""
                select * from voice.audio_artifact
                 where state <> 'DELETED'
                   and (expires_at <= :now or state in ('DELETE_QUEUED','DELETE_PARTIAL'))
                 order by expires_at, tenant_id, artifact_id
                 limit :limit
                """, new MapSqlParameterSource()
                .addValue("now", JdbcPersistenceSupport.writeInstant(now)).addValue("limit", limit),
                (row, rowNum) -> mapArtifact(row));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveArtifact(AudioArtifact artifact) {
        Optional<AudioArtifact> persisted = findArtifact(artifact.tenantId(), artifact.id());
        if (persisted.isPresent()
                && persisted.orElseThrow().version().equals(artifact.version())) {
            if (sameArtifact(persisted.orElseThrow(), artifact)) {
                return;
            }
            throw new OptimisticLockingFailureException(
                    "audio artifact state changed without a new aggregate version");
        }
        EncryptedEnvelope objectEnvelope = artifact.storageObjectRef()
                .map(ref -> cipher.encrypt(artifact.tenantId(), objectBinding(artifact.id()),
                        ref.opaqueReference()))
                .orElse(null);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", artifact.tenantId().value())
                .addValue("artifactId", artifact.id().value())
                .addValue("sessionId", artifact.sessionId().value())
                .addValue("turnId", artifact.turnId().value())
                .addValue("purpose", artifact.purpose().name())
                .addValue("consentId", artifact.consentRecordId().value())
                .addValue("createdAt", JdbcPersistenceSupport.writeInstant(artifact.createdAt()))
                .addValue("expiresAt", JdbcPersistenceSupport.writeInstant(artifact.expiresAt()))
                .addValue("state", artifact.state().name())
                .addValue("codec", artifact.codec().orElse(null))
                .addValue("sampleRate", artifact.sampleRate().orElse(null))
                .addValue("channelCount", artifact.channelCount().orElse(null))
                .addValue("byteCount", artifact.bytes().orElse(null))
                .addValue("durationMillis", artifact.durationMillis().orElse(null))
                .addValue("contentHash", artifact.contentHash().orElse(null))
                .addValue("providerInvocationId", artifact.providerInvocationId()
                        .map(ResourceId::value).orElse(null))
                .addValue("deleteQueuedAt", artifact.deleteQueuedAt()
                        .map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("deletedAt", artifact.deletedAt()
                        .map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("failureCode", artifact.failureCode().orElse(null))
                .addValue("version", artifact.version().value());
        JdbcPersistenceSupport.addEnvelope(parameters, "object", objectEnvelope);
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into voice.audio_artifact (
                    tenant_id, artifact_id, session_id, turn_id, purpose, consent_record_id,
                    created_at, expires_at, state, codec, sample_rate, channel_count,
                    byte_count, duration_millis,
                    object_key_id, object_algorithm, object_nonce, object_ciphertext, object_aad_hash,
                    content_hash, provider_invocation_id, delete_queued_at, deleted_at,
                    failure_code, aggregate_version
                ) values (
                    :tenantId, :artifactId, :sessionId, :turnId, :purpose, :consentId,
                    :createdAt, :expiresAt, :state, :codec, :sampleRate, :channelCount,
                    :byteCount, :durationMillis,
                    :objectKeyId, :objectAlgorithm, :objectNonce, :objectCiphertext, :objectAadHash,
                    :contentHash, :providerInvocationId, :deleteQueuedAt, :deletedAt,
                    :failureCode, :version
                ) on conflict do nothing
                """, """
                update voice.audio_artifact
                   set state = :state, codec = :codec, sample_rate = :sampleRate,
                       channel_count = :channelCount, byte_count = :byteCount,
                       duration_millis = :durationMillis,
                       object_key_id = :objectKeyId, object_algorithm = :objectAlgorithm,
                       object_nonce = :objectNonce, object_ciphertext = :objectCiphertext,
                       object_aad_hash = :objectAadHash, content_hash = :contentHash,
                       provider_invocation_id = :providerInvocationId,
                       delete_queued_at = :deleteQueuedAt, deleted_at = :deletedAt,
                       failure_code = :failureCode, aggregate_version = :version
                 where tenant_id = :tenantId and artifact_id = :artifactId
                   and aggregate_version = :expectedVersion
                """, parameters, artifact.version().value(),
                "audio artifact " + artifact.tenantId().value() + "/" + artifact.id().value());
    }

    private static boolean sameArtifact(AudioArtifact left, AudioArtifact right) {
        return left.id().equals(right.id())
                && left.tenantId().equals(right.tenantId())
                && left.sessionId().equals(right.sessionId())
                && left.turnId().equals(right.turnId())
                && left.purpose() == right.purpose()
                && left.consentRecordId().equals(right.consentRecordId())
                && left.createdAt().equals(right.createdAt())
                && left.expiresAt().equals(right.expiresAt())
                && left.state() == right.state()
                && left.codec().equals(right.codec())
                && left.sampleRate().equals(right.sampleRate())
                && left.channelCount().equals(right.channelCount())
                && left.bytes().equals(right.bytes())
                && left.durationMillis().equals(right.durationMillis())
                && left.storageObjectRef().equals(right.storageObjectRef())
                && left.contentHash().equals(right.contentHash())
                && left.providerInvocationId().equals(right.providerInvocationId())
                && left.deleteQueuedAt().equals(right.deleteQueuedAt())
                && left.deletedAt().equals(right.deletedAt())
                && left.failureCode().equals(right.failureCode())
                && left.version().equals(right.version());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTranscript(Transcript transcript) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", transcript.tenantId().value())
                .addValue("transcriptId", transcript.id().value())
                .addValue("sessionId", transcript.sessionId().value())
                .addValue("turnId", transcript.turnId().value())
                .addValue("artifactId", transcript.audioArtifactId().value())
                .addValue("state", transcript.state().name())
                .addValue("confirmedVersionId", transcript.confirmedVersionId()
                        .map(ResourceId::value).orElse(null))
                .addValue("confirmedBy", transcript.confirmedBy().map(JdbcVoiceRepository::ruoyiUserId).orElse(null))
                .addValue("confirmedAt", transcript.confirmedAt()
                        .map(JdbcPersistenceSupport::writeInstant).orElse(null))
                .addValue("version", transcript.version().value());
        int inserted = jdbc.update("""
                insert into voice.transcript (
                    tenant_id, transcript_id, session_id, turn_id, audio_artifact_id,
                    state, confirmed_version_id, confirmed_by_ruoyi_user_id, confirmed_at, aggregate_version
                ) values (
                    :tenantId, :transcriptId, :sessionId, :turnId, :artifactId,
                    :state, :confirmedVersionId, :confirmedBy, :confirmedAt, :version
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            PersistedTranscript persisted = loadPersistedTranscript(transcript.tenantId(), transcript.id());
            validateTranscriptAdvance(persisted, transcript);
            parameters.addValue("expectedVersion", persisted.aggregateVersion());
            int updated = jdbc.update("""
                    update voice.transcript
                       set state = :state, confirmed_version_id = :confirmedVersionId,
                           confirmed_by_ruoyi_user_id = :confirmedBy, confirmed_at = :confirmedAt,
                           aggregate_version = :version
                     where tenant_id = :tenantId and transcript_id = :transcriptId
                       and aggregate_version = :expectedVersion
                    """, parameters);
            if (updated != 1) {
                throw new OptimisticLockingFailureException("transcript aggregate version conflict");
            }
        }
        transcript.versions().forEach(this::appendTranscriptVersion);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveExecution(VoiceTurnExecution execution) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", execution.tenantId().value())
                .addValue("executionId", execution.id().value())
                .addValue("sessionId", execution.sessionId().value())
                .addValue("turnId", execution.turnId().value())
                .addValue("state", execution.state().name())
                .addValue("inputArtifactId", execution.inputArtifactId().map(ResourceId::value).orElse(null))
                .addValue("transcriptId", execution.transcriptId().map(ResourceId::value).orElse(null))
                .addValue("confirmedTranscriptVersionId", execution.confirmedTranscriptVersionId()
                        .map(ResourceId::value).orElse(null))
                .addValue("outputArtifactId", execution.outputArtifactId().map(ResourceId::value).orElse(null))
                .addValue("socketGeneration", execution.socketGeneration())
                .addValue("lastClientSequence", execution.lastClientSequence())
                .addValue("lastServerSequence", execution.lastServerSequence())
                .addValue("degradationReason", execution.degradationReason().orElse(null))
                .addValue("version", execution.version().value());
        JdbcPersistenceSupport.versionedUpsert(jdbc, """
                insert into voice.turn_execution (
                    tenant_id, execution_id, session_id, turn_id, state,
                    input_artifact_id, transcript_id, confirmed_transcript_version_id,
                    output_artifact_id, socket_generation, last_client_sequence,
                    last_server_sequence, degradation_reason, aggregate_version
                ) values (
                    :tenantId, :executionId, :sessionId, :turnId, :state,
                    :inputArtifactId, :transcriptId, :confirmedTranscriptVersionId,
                    :outputArtifactId, :socketGeneration, :lastClientSequence,
                    :lastServerSequence, :degradationReason, :version
                ) on conflict do nothing
                """, """
                update voice.turn_execution
                   set state = :state, input_artifact_id = :inputArtifactId,
                       transcript_id = :transcriptId,
                       confirmed_transcript_version_id = :confirmedTranscriptVersionId,
                       output_artifact_id = :outputArtifactId,
                       socket_generation = :socketGeneration,
                       last_client_sequence = :lastClientSequence,
                       last_server_sequence = :lastServerSequence,
                       degradation_reason = :degradationReason, aggregate_version = :version
                 where tenant_id = :tenantId and execution_id = :executionId
                   and aggregate_version = :expectedVersion
                """, parameters, execution.version().value(),
                "voice execution " + execution.tenantId().value() + "/" + execution.id().value());
    }

    private AudioArtifact mapArtifact(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId artifactId = ResourceId.of(row.getString("artifact_id"));
        EncryptedEnvelope objectEnvelope = JdbcPersistenceSupport.readNullableEnvelope(row, "object");
        StorageObjectRef objectRef = objectEnvelope == null ? null : new StorageObjectRef(
                cipher.decrypt(tenantId, objectBinding(artifactId), objectEnvelope));
        return AudioArtifact.rehydrate(artifactId, tenantId,
                ResourceId.of(row.getString("session_id")), ResourceId.of(row.getString("turn_id")),
                AudioPurpose.valueOf(row.getString("purpose")),
                ResourceId.of(row.getString("consent_record_id")),
                JdbcPersistenceSupport.readInstant(row, "created_at"),
                JdbcPersistenceSupport.readInstant(row, "expires_at"),
                AudioArtifactState.valueOf(row.getString("state")), row.getString("codec"),
                (Integer) row.getObject("sample_rate"), (Integer) row.getObject("channel_count"),
                (Long) row.getObject("byte_count"), (Long) row.getObject("duration_millis"), objectRef,
                row.getString("content_hash"), nullableResource(row.getString("provider_invocation_id")),
                JdbcPersistenceSupport.readNullableInstant(row, "delete_queued_at"),
                JdbcPersistenceSupport.readNullableInstant(row, "deleted_at"), row.getString("failure_code"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private Transcript mapTranscript(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId transcriptId = ResourceId.of(row.getString("transcript_id"));
        List<TranscriptVersion> versions = jdbc.query("""
                select * from voice.transcript_version
                 where tenant_id = :tenantId and transcript_id = :transcriptId
                 order by version_no
                """, Map.of("tenantId", tenantId.value(), "transcriptId", transcriptId.value()),
                (versionRow, rowNum) -> mapTranscriptVersion(versionRow));
        return Transcript.rehydrate(transcriptId, tenantId,
                ResourceId.of(row.getString("session_id")), ResourceId.of(row.getString("turn_id")),
                ResourceId.of(row.getString("audio_artifact_id")),
                TranscriptState.valueOf(row.getString("state")), versions,
                nullableResource(row.getString("confirmed_version_id")),
                nullableUser(row.getString("confirmed_by_ruoyi_user_id")),
                JdbcPersistenceSupport.readNullableInstant(row, "confirmed_at"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private TranscriptVersion mapTranscriptVersion(ResultSet row) throws SQLException {
        TenantId tenantId = TenantId.of(row.getString("tenant_id"));
        ResourceId versionId = ResourceId.of(row.getString("transcript_version_id"));
        String text = cipher.decrypt(tenantId, transcriptBinding(versionId),
                JdbcPersistenceSupport.readEnvelope(row, "body"));
        List<ConfidenceSpan> spans = jdbc.query("""
                select * from voice.transcript_confidence_span
                 where tenant_id = :tenantId and transcript_version_id = :versionId
                 order by span_no
                """, Map.of("tenantId", tenantId.value(), "versionId", versionId.value()),
                (spanRow, rowNum) -> new ConfidenceSpan(spanRow.getInt("start_inclusive"),
                        spanRow.getInt("end_exclusive"), spanRow.getBigDecimal("confidence")));
        return new TranscriptVersion(versionId, tenantId,
                ResourceId.of(row.getString("transcript_id")), row.getInt("version_no"),
                TranscriptSource.valueOf(row.getString("source")), text, row.getString("content_hash"),
                row.getString("language"), row.getString("offset_unit"), spans,
                optionalResource(row.getString("provider_invocation_id")),
                optionalUser(row.getString("corrected_by_ruoyi_user_id")),
                optionalResource(row.getString("supersedes_id")),
                JdbcPersistenceSupport.readInstant(row, "created_at"));
    }

    private VoiceTurnExecution mapExecution(ResultSet row) throws SQLException {
        return VoiceTurnExecution.rehydrate(ResourceId.of(row.getString("execution_id")),
                TenantId.of(row.getString("tenant_id")), ResourceId.of(row.getString("session_id")),
                ResourceId.of(row.getString("turn_id")), VoiceTurnState.valueOf(row.getString("state")),
                nullableResource(row.getString("input_artifact_id")),
                nullableResource(row.getString("transcript_id")),
                nullableResource(row.getString("confirmed_transcript_version_id")),
                nullableResource(row.getString("output_artifact_id")),
                row.getLong("socket_generation"), row.getLong("last_client_sequence"),
                row.getLong("last_server_sequence"), row.getString("degradation_reason"),
                new AggregateVersion(row.getLong("aggregate_version")));
    }

    private void appendTranscriptVersion(TranscriptVersion version) {
        EncryptedEnvelope body = cipher.encrypt(version.tenantId(), transcriptBinding(version.id()),
                version.text());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", version.tenantId().value())
                .addValue("versionId", version.id().value())
                .addValue("transcriptId", version.transcriptId().value())
                .addValue("versionNo", version.versionNo())
                .addValue("source", version.source().name())
                .addValue("contentHash", version.contentHash())
                .addValue("language", version.language())
                .addValue("offsetUnit", version.offsetUnit())
                .addValue("providerInvocationId", version.providerInvocationId()
                        .map(ResourceId::value).orElse(null))
                .addValue("correctedBy", version.correctedBy().map(JdbcVoiceRepository::ruoyiUserId).orElse(null))
                .addValue("supersedesId", version.supersedesId().map(ResourceId::value).orElse(null))
                .addValue("createdAt", JdbcPersistenceSupport.writeInstant(version.createdAt()));
        JdbcPersistenceSupport.addEnvelope(parameters, "body", body);
        int inserted = jdbc.update("""
                insert into voice.transcript_version (
                    tenant_id, transcript_version_id, transcript_id, version_no, source, content_hash,
                    body_key_id, body_algorithm, body_nonce, body_ciphertext, body_aad_hash,
                    language, offset_unit, provider_invocation_id, corrected_by_ruoyi_user_id, supersedes_id, created_at
                ) values (
                    :tenantId, :versionId, :transcriptId, :versionNo, :source, :contentHash,
                    :bodyKeyId, :bodyAlgorithm, :bodyNonce, :bodyCiphertext, :bodyAadHash,
                    :language, :offsetUnit, :providerInvocationId, :correctedBy, :supersedesId, :createdAt
                ) on conflict do nothing
                """, parameters);
        if (inserted == 0) {
            Boolean identical = jdbc.queryForObject("""
                    select content_hash = :contentHash and transcript_id = :transcriptId
                           and version_no = :versionNo and source = :source
                           and language = :language and offset_unit = :offsetUnit
                           and provider_invocation_id is not distinct from :providerInvocationId
                           and corrected_by_ruoyi_user_id is not distinct from :correctedBy
                           and supersedes_id is not distinct from :supersedesId
                           and created_at = :createdAt
                      from voice.transcript_version
                     where tenant_id = :tenantId and transcript_version_id = :versionId
                    """, parameters, Boolean.class);
            if (!Boolean.TRUE.equals(identical)) {
                throw new DataIntegrityViolationException("immutable transcript version id collision");
            }
        }
        for (int index = 0; index < version.lowConfidenceSpans().size(); index++) {
            ConfidenceSpan span = version.lowConfidenceSpans().get(index);
            MapSqlParameterSource spanParameters = new MapSqlParameterSource()
                    .addValue("tenantId", version.tenantId().value())
                    .addValue("versionId", version.id().value())
                    .addValue("spanNo", index + 1)
                    .addValue("startInclusive", span.startInclusive())
                    .addValue("endExclusive", span.endExclusive())
                    .addValue("confidence", span.confidence());
            int spanInserted = jdbc.update("""
                    insert into voice.transcript_confidence_span (
                        tenant_id, transcript_version_id, span_no,
                        start_inclusive, end_exclusive, confidence
                    ) values (
                        :tenantId, :versionId, :spanNo,
                        :startInclusive, :endExclusive, :confidence
                    ) on conflict do nothing
                    """, spanParameters);
            if (spanInserted == 0) {
                Boolean identicalSpan = jdbc.queryForObject("""
                        select start_inclusive = :startInclusive
                               and end_exclusive = :endExclusive
                               and confidence = :confidence
                          from voice.transcript_confidence_span
                         where tenant_id = :tenantId and transcript_version_id = :versionId
                           and span_no = :spanNo
                        """, spanParameters, Boolean.class);
                if (!Boolean.TRUE.equals(identicalSpan)) {
                    throw new DataIntegrityViolationException(
                            "immutable transcript confidence span collision");
                }
            }
        }
        Integer persistedSpanCount = jdbc.queryForObject("""
                select count(*)
                  from voice.transcript_confidence_span
                 where tenant_id = :tenantId and transcript_version_id = :versionId
                """, parameters, Integer.class);
        if (persistedSpanCount == null
                || persistedSpanCount != version.lowConfidenceSpans().size()) {
            throw new DataIntegrityViolationException(
                    "immutable transcript confidence span count collision");
        }
    }

    private PersistedTranscript loadPersistedTranscript(TenantId tenantId, ResourceId transcriptId) {
        List<PersistedTranscript> rows = jdbc.query("""
                select aggregate_version, state, confirmed_version_id,
                       session_id, turn_id, audio_artifact_id
                  from voice.transcript
                 where tenant_id = :tenantId and transcript_id = :transcriptId
                """, Map.of("tenantId", tenantId.value(), "transcriptId", transcriptId.value()),
                (row, rowNum) -> new PersistedTranscript(row.getLong("aggregate_version"),
                        TranscriptState.valueOf(row.getString("state")),
                        row.getString("confirmed_version_id"), row.getString("session_id"),
                        row.getString("turn_id"), row.getString("audio_artifact_id"),
                        jdbc.query("""
                                select transcript_version_id from voice.transcript_version
                                 where tenant_id = :tenantId and transcript_id = :transcriptId
                                 order by version_no
                                """, Map.of("tenantId", tenantId.value(),
                                "transcriptId", transcriptId.value()),
                                (versionRow, versionRowNum) -> versionRow.getString("transcript_version_id"))));
        if (rows.size() != 1) {
            throw new DataIntegrityViolationException("persisted transcript disappeared during save");
        }
        return rows.get(0);
    }

    private static void validateTranscriptAdvance(PersistedTranscript persisted, Transcript incoming) {
        if (!persisted.sessionId().equals(incoming.sessionId().value())
                || !persisted.turnId().equals(incoming.turnId().value())
                || !persisted.audioArtifactId().equals(incoming.audioArtifactId().value())) {
            throw new DataIntegrityViolationException("immutable transcript ownership collision");
        }
        List<String> incomingIds = incoming.versions().stream().map(version -> version.id().value()).toList();
        if (incomingIds.size() < persisted.versionIds().size()
                || !incomingIds.subList(0, persisted.versionIds().size()).equals(persisted.versionIds())) {
            throw new OptimisticLockingFailureException("transcript version chain changed concurrently");
        }
        int appendedVersions = incomingIds.size() - persisted.versionIds().size();
        long expectedAdvance;
        String incomingConfirmed = incoming.confirmedVersionId().map(ResourceId::value).orElse(null);
        if (persisted.state() == incoming.state() && appendedVersions == 0
                && java.util.Objects.equals(persisted.confirmedVersionId(), incomingConfirmed)) {
            expectedAdvance = 0;
        } else if (persisted.state() == TranscriptState.OPEN
                && incoming.state() == TranscriptState.ASR_FINAL && appendedVersions >= 1) {
            expectedAdvance = appendedVersions;
        } else if (persisted.state() == TranscriptState.OPEN
                && incoming.state() == TranscriptState.CONFIRMED && appendedVersions >= 1) {
            expectedAdvance = appendedVersions + 1L;
        } else if (persisted.state() == TranscriptState.ASR_FINAL
                && incoming.state() == TranscriptState.ASR_FINAL && appendedVersions >= 1) {
            expectedAdvance = appendedVersions;
        } else if (persisted.state() == TranscriptState.ASR_FINAL
                && incoming.state() == TranscriptState.CONFIRMED) {
            expectedAdvance = appendedVersions + 1L;
        } else if ((persisted.state() == TranscriptState.OPEN
                || persisted.state() == TranscriptState.ASR_FINAL)
                && incoming.state() == TranscriptState.CANCELLED) {
            expectedAdvance = appendedVersions + 1L;
        } else {
            throw new OptimisticLockingFailureException("transcript state changed concurrently or illegally");
        }
        if (incoming.version().value() != persisted.aggregateVersion() + expectedAdvance) {
            throw new OptimisticLockingFailureException("transcript aggregate version jump is inconsistent");
        }
    }

    private static ResourceId nullableResource(String value) {
        return value == null ? null : ResourceId.of(value);
    }

    private static UserId nullableUser(String value) {
        return value == null ? null : UserId.of(value);
    }

    private static Optional<ResourceId> optionalResource(String value) {
        return value == null ? Optional.empty() : Optional.of(ResourceId.of(value));
    }

    private static Optional<UserId> optionalUser(String value) {
        return value == null ? Optional.empty() : Optional.of(UserId.of(value));
    }

    private static long ruoyiUserId(UserId userId) {
        return Long.parseLong(userId.value());
    }

    private static String objectBinding(ResourceId artifactId) {
        return "voice.audio-artifact:" + artifactId.value() + ":object-reference";
    }

    private static String transcriptBinding(ResourceId versionId) {
        return "voice.transcript-version:" + versionId.value() + ":body";
    }

    private record PersistedTranscript(
            long aggregateVersion,
            TranscriptState state,
            String confirmedVersionId,
            String sessionId,
            String turnId,
            String audioArtifactId,
            List<String> versionIds
    ) { }
}

