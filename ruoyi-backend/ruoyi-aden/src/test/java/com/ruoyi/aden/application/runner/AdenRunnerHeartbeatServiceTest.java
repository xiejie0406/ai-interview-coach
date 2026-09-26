package com.ruoyi.aden.application.runner;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;
import com.ruoyi.aden.domain.runner.AdenDeliveryId;
import com.ruoyi.aden.domain.runner.AdenRunnerId;
import com.ruoyi.aden.domain.runner.AdenSessionId;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenRunnerHeartbeatServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T02:00:00Z");
    private static final AdenWorkspaceId WORKSPACE = new AdenWorkspaceId("11111111-1111-4111-8111-111111111111");
    private static final AdenSessionId SESSION = new AdenSessionId("22222222-2222-4222-8222-222222222222");
    private static final AdenDeliveryId DELIVERY = new AdenDeliveryId("33333333-3333-4333-8333-333333333333");
    private static final AdenRunnerSessionPrincipal PRINCIPAL = new AdenRunnerSessionPrincipal(
            WORKSPACE, new AdenRunnerId("44444444-4444-4444-8444-444444444444"), SESSION, 2);

    @Test
    void renewsSessionAndDeliveryAndReturnsCancelInstruction() {
        FakeRepository repository = new FakeRepository();
        repository.cancelRequested = true;
        var result = new AdenRunnerHeartbeatService(repository, 900, 60).heartbeat(
                new AdenRunnerHeartbeatService.HeartbeatCommand(PRINCIPAL, 1,
                        List.of(new AdenRunnerHeartbeatService.DeliveryPulse(DELIVERY, 7, 1))));

        assertEquals(1, repository.sessionSequence);
        assertEquals(1, result.deliveries().size());
        assertEquals(true, result.deliveries().get(0).cancelRequested());
        assertEquals(NOW.plusSeconds(60), result.deliveries().get(0).leaseUntil());
    }

    @Test
    void rejectsEpochRaceAndLostFenceWithStableCodes() {
        FakeRepository stale = new FakeRepository();
        stale.session = new AdenRunnerDeliveryRepository.SessionCapacity(
                WORKSPACE, SESSION, 2, 3, "ACTIVE", 2, 0, 0,
                NOW.plusSeconds(60), NOW);
        AdenApplicationException epoch = assertThrows(AdenApplicationException.class,
                () -> new AdenRunnerHeartbeatService(stale, 900, 60).heartbeat(
                        new AdenRunnerHeartbeatService.HeartbeatCommand(PRINCIPAL, 1, List.of())));
        assertEquals("ADEN_SESSION_EPOCH_STALE", epoch.errorCode());

        FakeRepository lost = new FakeRepository();
        lost.live = false;
        AdenApplicationException lease = assertThrows(AdenApplicationException.class,
                () -> new AdenRunnerHeartbeatService(lost, 900, 60).heartbeat(
                        new AdenRunnerHeartbeatService.HeartbeatCommand(PRINCIPAL, 1,
                                List.of(new AdenRunnerHeartbeatService.DeliveryPulse(DELIVERY, 7, 1)))));
        assertEquals("ADEN_RUNNER_LEASE_LOST", lease.errorCode());
    }

    private static final class FakeRepository implements AdenRunnerDeliveryRepository {
        private SessionCapacity session = new SessionCapacity(WORKSPACE, SESSION, 2, 2,
                "ACTIVE", 2, 1, 0, NOW.plusSeconds(60), NOW);
        private boolean live = true;
        private boolean cancelRequested;
        private long sessionSequence;
        @Override public long lockWorkspace(AdenWorkspaceId workspaceId) { return 0; }
        @Override public Optional<ClaimInbox> findClaimInboxForUpdate(AdenWorkspaceId w,AdenSessionId s,AdenIdempotencyKey k){return Optional.empty();}
        @Override public void insertClaimInbox(ClaimInbox inbox){}
        @Override public void completeClaimInbox(AdenWorkspaceId w,String id,String json,Instant now){}
        @Override public Optional<SessionCapacity> lockSession(AdenRunnerSessionPrincipal principal){return Optional.of(session);}
        @Override public List<ClaimCandidate> lockReady(AdenWorkspaceId w,AdenCapabilityCode c,int limit){return List.of();}
        @Override public ClaimedDelivery claim(ClaimCandidate c,AdenRunnerSessionPrincipal p,int ttl){throw new UnsupportedOperationException();}
        @Override public void increaseInFlight(SessionCapacity session,int count){}
        @Override public boolean isLive(ClaimedDelivery delivery){return live;}
        @Override public void heartbeatSession(SessionCapacity value,long sequence,int ttl){sessionSequence=sequence;}
        @Override public List<AdenDeliveryId> findCancelRequested(AdenRunnerSessionPrincipal p,List<AdenDeliveryId> ids){return cancelRequested ? ids : List.of();}
        @Override public Optional<HeartbeatDelivery> heartbeatDelivery(AdenRunnerSessionPrincipal principal,
                AdenDeliveryId deliveryId,long fence,long sequence,int ttl){
            return live ? Optional.of(new HeartbeatDelivery(deliveryId,"RUNNING",fence,sequence,
                    NOW.plusSeconds(ttl),cancelRequested,NOW)) : Optional.empty();
        }
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
