package com.aiinterviewcoach.application.voice.internal;

import com.aiinterviewcoach.application.integration.port.ObjectStoragePort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.application.shared.ApplicationErrorCode;
import com.aiinterviewcoach.application.shared.ApplicationException;
import com.aiinterviewcoach.application.voice.DeleteAudioArtifact;
import com.aiinterviewcoach.application.voice.port.VoiceRepository;
import com.aiinterviewcoach.domain.platform.EventContext;
import com.aiinterviewcoach.domain.voice.AudioArtifactState;

import java.util.Map;

/** 对象删除在数据库事务外执行；状态事实通过前后两个短事务保存。 */
public final class DefaultDeleteAudioArtifact implements DeleteAudioArtifact {

    private final VoiceRepository repository;
    private final ObjectStoragePort objectStorage;
    private final DomainEventPort domainEvents;
    private final TransactionPort transaction;

    public DefaultDeleteAudioArtifact(VoiceRepository repository, ObjectStoragePort objectStorage,
                                      DomainEventPort domainEvents, TransactionPort transaction) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.objectStorage = java.util.Objects.requireNonNull(objectStorage);
        this.domainEvents = java.util.Objects.requireNonNull(domainEvents);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }

    @Override
    public Result handle(Command command) {
        var context = new EventContext(command.correlationId(), command.requestedAt());
        var queued = transaction.required(() -> {
            var artifact = requireArtifact(command);
            if (artifact.state() == AudioArtifactState.DELETED) {
                return artifact;
            }
            artifact.version().requireMatches(command.expectedVersion());
            if (artifact.state() == AudioArtifactState.DELETE_PARTIAL) {
                artifact.retryDeletion(artifact.version(), context);
            } else if (artifact.state() != AudioArtifactState.DELETE_QUEUED) {
                artifact.queueDeletion(artifact.version(), context);
            }
            repository.saveArtifact(artifact);
            domainEvents.append(artifact.pullDomainEvents());
            return artifact;
        });
        if (queued.state() == AudioArtifactState.DELETED) {
            return new Result(queued.id(), queued.state(), queued.version(), false);
        }
        if (queued.storageObjectRef().isEmpty()) {
            return finishWithoutExternalObject(command, context);
        }

        var deletion = objectStorage.delete(new ObjectStoragePort.DeleteRequest(
                command.tenantId(), command.artifactId(), queued.storageObjectRef().orElseThrow()));
        return transaction.required(() -> {
            var current = requireArtifact(command);
            if (deletion.deleted()) {
                current.markDeleted(current.version(), context);
            } else {
                current.markDeletePartial(deletion.failureCode().orElse("OBJECT_DELETE_FAILED"),
                        current.version(), context);
            }
            repository.saveArtifact(current);
            domainEvents.append(current.pullDomainEvents());
            return new Result(current.id(), current.state(), current.version(), true);
        });
    }

    private Result finishWithoutExternalObject(Command command, EventContext context) {
        return transaction.required(() -> {
            var current = requireArtifact(command);
            current.markDeleted(current.version(), context);
            repository.saveArtifact(current);
            domainEvents.append(current.pullDomainEvents());
            return new Result(current.id(), current.state(), current.version(), false);
        });
    }

    private com.aiinterviewcoach.domain.voice.AudioArtifact requireArtifact(Command command) {
        return repository.findArtifact(command.tenantId(), command.artifactId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "audio artifact was not found", false, Map.of()));
    }
}
