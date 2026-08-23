package com.aiinterviewcoach.adapters.inbound.rest.voice;

import com.aiinterviewcoach.domain.governance.ConsentPurpose;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;

import java.util.Map;

/** 本地 MVP 的服务端权威语音政策引用；正文仍由前端静态政策页面承载。 */
public final class VoicePolicyCatalog {
    private VoicePolicyCatalog() { }

    public static final Map<ConsentPurpose, ImmutableVersionRef> POLICIES = Map.of(
            ConsentPurpose.VOICE_CAPTURE, new ImmutableVersionRef(
                    ResourceId.of("10000000-0000-4000-8000-000000000001"), 1,
                    "local-voice-capture-v1"),
            ConsentPurpose.MODEL_PROCESSING, new ImmutableVersionRef(
                    ResourceId.of("10000000-0000-4000-8000-000000000002"), 1,
                    "local-model-processing-v1"));
}
