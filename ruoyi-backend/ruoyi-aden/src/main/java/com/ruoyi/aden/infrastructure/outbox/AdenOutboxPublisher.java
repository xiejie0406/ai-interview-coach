package com.ruoyi.aden.infrastructure.outbox;

import com.ruoyi.aden.application.event.AdenOutboxRecoveryService;
import com.ruoyi.aden.infrastructure.stream.AdenWorkspaceEventBroadcaster;

/** claim / publish / acknowledge 分离；publish 只发送 workspace wake-up。 */
public final class AdenOutboxPublisher {
    private final AdenOutboxRecoveryService outbox;
    private final AdenWorkspaceEventBroadcaster broadcaster;

    public AdenOutboxPublisher(AdenOutboxRecoveryService outbox,
                               AdenWorkspaceEventBroadcaster broadcaster) {
        this.outbox = outbox;
        this.broadcaster = broadcaster;
    }

    public int publishOnce(int limit) {
        var messages = outbox.claim(limit);
        for (var message : messages) {
            try {
                broadcaster.wake(message.workspaceId());
                outbox.acknowledge(message);
            } catch (RuntimeException exception) {
                outbox.fail(message, exception);
            }
        }
        return messages.size();
    }
}
