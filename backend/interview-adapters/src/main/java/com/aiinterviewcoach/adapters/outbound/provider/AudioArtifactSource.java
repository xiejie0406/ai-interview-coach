package com.aiinterviewcoach.adapters.outbound.provider;

import com.aiinterviewcoach.domain.platform.ArtifactRef;
import com.aiinterviewcoach.domain.platform.TenantId;

import java.util.Arrays;

/**
 * ASR adapter 读取受控音频正文的窄边界。
 *
 * <p>对象存储 key、签名 URL 与供应商存储类型不能越过该边界。实现方还必须按 tenant
 * 校验 artifact 所有权，并在返回前限制音频大小。</p>
 */
@FunctionalInterface
public interface AudioArtifactSource {

    AudioContent load(TenantId tenantId, ArtifactRef artifactRef);

    record AudioContent(
            byte[] bytes,
            String format,
            String codec,
            int sampleRate,
            int bitsPerSample,
            int channels
    ) {
        public AudioContent {
            bytes = Arrays.copyOf(bytes, bytes.length);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("audio bytes must not be empty");
            }
            format = requireText(format, "audio format");
            codec = requireText(codec, "audio codec");
            if (sampleRate <= 0 || bitsPerSample <= 0 || channels <= 0) {
                throw new IllegalArgumentException("audio metadata must be positive");
            }
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
