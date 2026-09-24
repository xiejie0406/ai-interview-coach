package com.ruoyi.aden.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenPropertiesTest {
    @Test
    void defaultsMatchCanonicalConfigurationContract() {
        AdenProperties properties = new AdenProperties();
        properties.afterPropertiesSet();

        assertEquals(900, properties.getRunner().getSession().getTtlSeconds());
        assertEquals(15, properties.getRunner().getSession().getHeartbeatIntervalSeconds());
        assertEquals(60, properties.getRunner().getDelivery().getLeaseTtlSeconds());
        assertEquals(8, properties.getRunner().getClaim().getMaxBatchSize());
        assertEquals(32, properties.getRunner().getSession().getMaxCapacity());
        assertEquals(15, properties.getStream().getHeartbeatIntervalSeconds());
        assertEquals(300, properties.getStream().getConnectionTtlSeconds());
        assertEquals(100, properties.getStream().getReplayBatchSize());
        assertEquals(256, properties.getStream().getPerConnectionQueueCapacity());
        assertEquals(10, properties.getStream().getSendTimeoutSeconds());
        assertEquals(30, properties.getOutbox().getClaimTtlSeconds());
        assertEquals(1000, properties.getOutbox().getRetry().getInitialDelayMilliseconds());
        assertEquals(60000, properties.getOutbox().getRetry().getMaxDelayMilliseconds());
        assertEquals(10, properties.getOutbox().getRetry().getMaxAttempts());
        assertEquals(120, properties.getIdempotency().getInProgressTtlSeconds());
        assertEquals(604800, properties.getIdempotency().getRetentionSeconds());
        assertEquals(604800, properties.getEvent().getRetentionSeconds());
    }

    @Test
    void rejectsCrossFieldInvariantViolation() {
        AdenProperties properties = new AdenProperties();
        properties.getRunner().getSession().setTtlSeconds(30);
        properties.getRunner().getSession().setHeartbeatIntervalSeconds(15);
        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void enabledGuardRequiresExplicitExpectedDatabase() {
        AdenProperties properties = new AdenProperties();
        properties.setEnabled(true);
        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }
}
