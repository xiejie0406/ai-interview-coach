package com.ruoyi.interview.infrastructure.provider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** 用真实容器元数据计算时长，不用文本长度猜测或伪造供应商计费量。 */
final class AudioContainerDuration {
    static long millis(byte[] bytes, String codec, int sampleRate) {
        if ("pcm".equals(codec)) return sampleRate > 0 ? bytes.length * 1000L / (sampleRate * 2L) : 0;
        var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length >= 12 && tag(bytes, 0, "RIFF") && tag(bytes, 8, "WAVE")) {
            long byteRate = 0;
            for (int offset = 12; offset + 8 <= bytes.length;) {
                long length = Integer.toUnsignedLong(data.getInt(offset + 4));
                if (tag(bytes, offset, "fmt ") && length >= 16 && offset + 24 <= bytes.length)
                    byteRate = Integer.toUnsignedLong(data.getInt(offset + 16));
                if (tag(bytes, offset, "data") && byteRate > 0)
                    return Math.min(length, bytes.length - offset - 8L) * 1000 / byteRate;
                long next = offset + 8L + length + (length & 1);
                if (next > bytes.length) break;
                offset = (int) next;
            }
        }
        long granule = 0; int preSkip = 0;
        for (int offset = 0; offset + 27 <= bytes.length && tag(bytes, offset, "OggS");) {
            int segments = bytes[offset + 26] & 255;
            int body = offset + 27 + segments;
            if (body > bytes.length) return 0;
            int length = 0;
            for (int i = offset + 27; i < body; i++) length += bytes[i] & 255;
            if (body + length > bytes.length) return 0;
            if (length >= 12 && tag(bytes, body, "OpusHead")) preSkip = Short.toUnsignedInt(data.getShort(body + 10));
            granule = Math.max(granule, data.getLong(offset + 6));
            offset = body + length;
        }
        return Math.max(0, granule - preSkip) * 1000 / 48000;
    }
    private static boolean tag(byte[] bytes, int offset, String value) {
        return offset + value.length() <= bytes.length && value.equals(new String(bytes, offset, value.length(), StandardCharsets.US_ASCII));
    }
}
