package com.aiinterviewcoach.domain.governance;

/** 同意目的必须分离记录，注册同意不能覆盖语音或模型处理目的。 */
public enum ConsentPurpose {
    SERVICE_TERMS,
    PRIVACY_NOTICE,
    VOICE_CAPTURE,
    MODEL_PROCESSING
}
