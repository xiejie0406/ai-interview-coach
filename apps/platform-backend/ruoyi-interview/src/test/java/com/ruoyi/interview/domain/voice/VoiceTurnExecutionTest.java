package com.ruoyi.interview.domain.voice;

import com.ruoyi.interview.domain.platform.AggregateVersion;
import com.ruoyi.interview.domain.platform.ResourceId;
import com.ruoyi.interview.domain.platform.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceTurnExecutionTest {
    @Test
    void ttsProtocolFramesAdvanceWhileThinkingAndNoOutputReturnsToIdle() {
        VoiceTurnExecution execution = VoiceTurnExecution.rehydrate(
                ResourceId.of("execution-a"), TenantId.of("tenant-a"),
                ResourceId.of("session-a"), ResourceId.of("turn-a"), VoiceTurnState.THINKING,
                ResourceId.of("input-a"), ResourceId.of("transcript-a"),
                ResourceId.of("version-a"), null, 1, 7, 3, null, new AggregateVersion(12));

        execution.acceptClientSequence(8, execution.version());
        execution.finishThinking(execution.version());
        execution.emitServerSequence(4, execution.version());

        assertEquals(8, execution.lastClientSequence());
        assertEquals(4, execution.lastServerSequence());
        assertEquals(VoiceTurnState.IDLE, execution.state());
    }
}
