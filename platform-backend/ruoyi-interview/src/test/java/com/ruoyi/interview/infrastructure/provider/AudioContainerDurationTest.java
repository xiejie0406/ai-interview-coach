package com.ruoyi.interview.infrastructure.provider;
import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;
class AudioContainerDurationTest {
    @Test void readsWaveDataAndRejectsTruncatedContainers() {
        byte[] wave=new byte[32044]; var b=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(32036).put("WAVEfmt ".getBytes()).putInt(16)
            .putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2)
            .putShort((short)16).put("data".getBytes()).putInt(32000);
        assertEquals(1000,AudioContainerDuration.millis(wave,"wav",16000));
        assertEquals(0,AudioContainerDuration.millis(new byte[4],"ogg_opus",24000));
        assertEquals(1000,AudioContainerDuration.millis(new byte[48000],"pcm",24000));
    }
}
