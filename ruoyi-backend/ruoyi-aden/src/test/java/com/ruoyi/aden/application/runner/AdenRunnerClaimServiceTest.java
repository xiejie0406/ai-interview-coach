package com.ruoyi.aden.application.runner;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.error.AdenIdempotencyKeyReusedException;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenRunnerClaimServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T02:00:00Z");
    private static final AdenWorkspaceId WORKSPACE = new AdenWorkspaceId("11111111-1111-4111-8111-111111111111");
    private static final AdenSessionId SESSION = new AdenSessionId("22222222-2222-4222-8222-222222222222");
    private static final AdenRunnerSessionPrincipal PRINCIPAL = new AdenRunnerSessionPrincipal(
            WORKSPACE, new AdenRunnerId("33333333-3333-4333-8333-333333333333"), SESSION, 3);

    @Test
    void sameKeyReplaysOriginalLiveBatchWithoutClaimingAgain() {
        FakeRepository repository = new FakeRepository();
        AdenRunnerClaimService service = service(repository);
        var command = command("claim:one", 2, 2);

        var first = service.claim(command);
        var replay = service.claim(command);

        assertEquals(2, first.items().size());
        assertEquals(first.items(), replay.items());
        assertEquals(2, repository.claimCalls);
        assertEquals(2, repository.inFlight);
        assertEquals(false, first.replayed());
        assertEquals(true, replay.replayed());
        assertTrue(first.items().stream().allMatch(item -> item.delivery().fenceToken() == 1));
    }

    @Test
    void sameKeyDifferentFingerprintConflicts() {
        FakeRepository repository = new FakeRepository();
        AdenRunnerClaimService service = service(repository);
        service.claim(command("claim:conflict", 2, 1));

        assertThrows(AdenIdempotencyKeyReusedException.class,
                () -> service.claim(command("claim:conflict", 2, 2)));
    }

    @Test
    void replayFailsWhenAnyOriginalLeaseIsNoLongerLive() {
        FakeRepository repository = new FakeRepository();
        AdenRunnerClaimService service = service(repository);
        var command = command("claim:lost", 2, 1);
        service.claim(command);
        repository.live = false;

        AdenApplicationException error = assertThrows(AdenApplicationException.class,
                () -> service.claim(command));
        assertEquals("ADEN_RUNNER_LEASE_LOST", error.errorCode());
        assertEquals(1, repository.claimCalls);
    }

    @Test
    void staleEpochAndCapacityMismatchNeverClaimDelivery() {
        FakeRepository staleRepository = new FakeRepository();
        staleRepository.session = new AdenRunnerDeliveryRepository.SessionCapacity(
                WORKSPACE, SESSION, 3, 4, "ACTIVE", 2, 0, 0,
                NOW.plusSeconds(60), NOW);
        AdenApplicationException stale = assertThrows(AdenApplicationException.class,
                () -> service(staleRepository).claim(command("claim:stale", 2, 1)));
        assertEquals("ADEN_RUNNER_SESSION_INVALID", stale.errorCode());

        FakeRepository capacityRepository = new FakeRepository();
        AdenApplicationException mismatch = assertThrows(AdenApplicationException.class,
                () -> service(capacityRepository).claim(command("claim:capacity", 1, 1)));
        assertEquals("ADEN_RUNNER_CAPACITY_MISMATCH", mismatch.errorCode());
        assertEquals(0, capacityRepository.claimCalls);
    }

    private static AdenRunnerClaimService service(FakeRepository repository) {
        ObjectMapper mapper = new ObjectMapper();
        AtomicLong ids = new AtomicLong();
        return new AdenRunnerClaimService(repository, new AdenRequestFingerprint(mapper), mapper,
                () -> String.format("aaaaaaaa-aaaa-4aaa-8aaa-%012d", ids.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC), 60, 8, 30, 3600);
    }

    private static AdenRunnerClaimService.ClaimCommand command(String key, int capacity, int batch) {
        return new AdenRunnerClaimService.ClaimCommand(PRINCIPAL, new AdenIdempotencyKey(key),
                Set.of(AdenCapabilityCode.CORE), capacity, batch);
    }

    private static final class FakeRepository implements AdenRunnerDeliveryRepository {
        private ClaimInbox inbox;
        private SessionCapacity session = new SessionCapacity(
                WORKSPACE, SESSION, 3, 3, "ACTIVE", 2, 0, 0,
                NOW.plusSeconds(60), NOW);
        private final List<ClaimCandidate> ready = new ArrayList<>(List.of(
                new ClaimCandidate(WORKSPACE, new AdenDeliveryId("44444444-4444-4444-8444-444444444444"), 0),
                new ClaimCandidate(WORKSPACE, new AdenDeliveryId("55555555-5555-4555-8555-555555555555"), 0),
                new ClaimCandidate(WORKSPACE, new AdenDeliveryId("66666666-6666-4666-8666-666666666666"), 0)));
        private boolean live = true;
        private int claimCalls;
        private int inFlight;

        @Override public long lockWorkspace(AdenWorkspaceId workspaceId) { return 0; }
        @Override public Optional<ClaimInbox> findClaimInboxForUpdate(AdenWorkspaceId w, AdenSessionId s, AdenIdempotencyKey k) {
            return Optional.ofNullable(inbox);
        }
        @Override public void insertClaimInbox(ClaimInbox value) { inbox = value; }
        @Override public void completeClaimInbox(AdenWorkspaceId w,String id,String response,Instant now) {
            inbox = new ClaimInbox(inbox.workspaceId(), inbox.inboxId(), inbox.sessionId(), inbox.key(),
                    inbox.requestHash(), "COMPLETED", response, null, inbox.retentionUntil(), inbox.createdAt());
        }
        @Override public Optional<SessionCapacity> lockSession(AdenRunnerSessionPrincipal principal) { return Optional.of(session); }
        @Override public List<ClaimCandidate> lockReady(AdenWorkspaceId w,AdenCapabilityCode c,int limit) {
            return ready.stream().limit(limit).toList();
        }
        @Override public ClaimedDelivery claim(ClaimCandidate candidate,AdenRunnerSessionPrincipal principal,int ttl) {
            claimCalls++;
            ready.remove(candidate);
            return new ClaimedDelivery(WORKSPACE, candidate.deliveryId(),
                    "77777777-7777-4777-8777-777777777777",
                    "88888888-8888-4888-8888-888888888888", 1, 1,
                    NOW.plusSeconds(ttl), "{\"schemaVersion\":1}", "a".repeat(64), SESSION, 3);
        }
        @Override public void increaseInFlight(SessionCapacity ignored,int count) { inFlight += count; }
        @Override public boolean isLive(ClaimedDelivery delivery) { return live; }
        @Override public void heartbeatSession(SessionCapacity value,long sequence,int ttl) { }
        @Override public List<AdenDeliveryId> findCancelRequested(AdenRunnerSessionPrincipal p,List<AdenDeliveryId> ids){return List.of();}
        @Override public Optional<HeartbeatDelivery> heartbeatDelivery(AdenRunnerSessionPrincipal principal,
                AdenDeliveryId deliveryId,long fence,long sequence,int ttl) { return Optional.empty(); }
        @Override public Optional<ClaimInbox> findReceiptInboxForUpdate(AdenWorkspaceId w,AdenSessionId s,AdenIdempotencyKey k){return Optional.empty();}
        @Override public void insertReceiptInbox(ClaimInbox inbox){}
        @Override public void completeReceiptInbox(AdenWorkspaceId w,String id,String json,Instant now){}
        @Override public Optional<ReceiptRoute> findReceiptRoute(AdenWorkspaceId w,AdenDeliveryId d){return Optional.empty();}
        @Override public Optional<ReceiptStep> lockReceiptStep(AdenWorkspaceId w,String s){return Optional.empty();}
        @Override public Optional<ReceiptDelivery> lockReceiptDelivery(AdenWorkspaceId w,AdenDeliveryId d){return Optional.empty();}
        @Override public void applyReceiptDelivery(ReceiptDelivery d,String state,long seq,String out,String code,String message){}
        @Override public void retryAfterReceipt(ReceiptDelivery d,String id,String body,String hash,long seq,String out,String code,String message){}
        @Override public void applyReceiptStep(ReceiptStep s,String state,String out,String code,String message){}
        @Override public void decreaseInFlight(AdenRunnerSessionPrincipal p,int count){}
        @Override public List<RecoveryRoute> findExpiredRoutes(AdenWorkspaceId w,int limit){return List.of();}
        @Override public void requeueExpiredUnstarted(ReceiptDelivery d){}
        @Override public void expireAsOutcomeUnknown(ReceiptDelivery d){}
        @Override public void retryExpiredStarted(ReceiptDelivery d,String id,String body,String hash){}
        @Override public void updateRecoveryStep(ReceiptStep s,String state,String code){}
    }
}
