package com.ruoyi.aden.application.projection;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.task.AdenTaskEtag;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenProtocolCodecsTest {
    private static final AdenWorkspaceId WORKSPACE =
            new AdenWorkspaceId("11111111-1111-4111-8111-111111111111");
    private static final AdenTaskId TASK = new AdenTaskId("22222222-2222-4222-8222-222222222222");

    @Test
    void pageAndStreamCursorRoundTripAndRejectTamperOrForeignBinding() {
        byte[] secret = new byte[32];
        Arrays.fill(secret, (byte) 9);
        AdenOpaqueCursorCodec codec = new AdenOpaqueCursorCodec(secret);
        Instant now = Instant.parse("2026-09-13T05:00:00Z");

        String page = codec.encodePage("TASK", WORKSPACE, "a".repeat(64), now, TASK.value());
        assertTrue(page.startsWith("aden-p1."));
        var decodedPage = codec.decodePage(page, "TASK", WORKSPACE, "a".repeat(64));
        assertEquals(now, decodedPage.positionAt());
        assertEquals(TASK.value(), decodedPage.positionId());
        assertThrows(AdenApplicationException.class,
                () -> codec.decodePage(page, "AUDIT", WORKSPACE, "a".repeat(64)));
        assertThrows(AdenApplicationException.class,
                () -> codec.decodePage(tamper(page), "TASK", WORKSPACE, "a".repeat(64)));

        String stream = codec.encodeStream(WORKSPACE, "b".repeat(64), Long.MAX_VALUE, now);
        assertTrue(stream.startsWith("aden-c1."));
        var decodedStream = codec.decodeStream(stream, WORKSPACE, "b".repeat(64));
        assertEquals(Long.MAX_VALUE, decodedStream.sequence());
        assertEquals(now, decodedStream.issuedAt());
        AdenApplicationException foreign = assertThrows(AdenApplicationException.class,
                () -> codec.decodeStream(stream,
                        new AdenWorkspaceId("33333333-3333-4333-8333-333333333333"), "b".repeat(64)));
        assertEquals("ADEN_STREAM_CURSOR_INVALID", foreign.errorCode());
    }

    @Test
    void taskEtagIsStrongBoundToAggregateAndKeepsLongMax() {
        String tag = AdenTaskEtag.encode(TASK.value(), Long.toString(Long.MAX_VALUE));
        assertEquals("\"task-" + TASK.value() + "-v9223372036854775807\"", tag);
        assertEquals(Long.MAX_VALUE, AdenTaskEtag.decode(tag, TASK).value());
        assertEquals("ADEN_PRECONDITION_REQUIRED", assertThrows(AdenApplicationException.class,
                () -> AdenTaskEtag.decode(null, TASK)).errorCode());
        assertEquals("ADEN_VERSION_CONFLICT", assertThrows(AdenApplicationException.class,
                () -> AdenTaskEtag.decode("W/" + tag, TASK)).errorCode());
    }

    private static String tamper(String value) {
        char last = value.charAt(value.length() - 1);
        return value.substring(0, value.length() - 1) + (last == 'A' ? 'B' : 'A');
    }
}
