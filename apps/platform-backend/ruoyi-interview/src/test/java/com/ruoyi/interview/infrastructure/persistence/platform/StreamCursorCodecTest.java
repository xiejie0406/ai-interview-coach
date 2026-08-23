package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.domain.platform.ResourceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamCursorCodecTest {
    @Test
    void roundTripsOpaqueCursorAndRejectsMalformedInput() {
        StreamCursorCodec codec = new StreamCursorCodec();
        String cursor = codec.encode(17, ResourceId.of("event-a"));

        var decoded = codec.decode(cursor).orElseThrow();
        assertEquals(17, decoded.sequence());
        assertEquals(ResourceId.of("event-a"), decoded.eventId());
        assertTrue(codec.decode("not-a-cursor").isEmpty());
        assertTrue(codec.decode("").isEmpty());
    }
}
