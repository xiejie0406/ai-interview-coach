package com.ruoyi.fashion.infrastructure.image;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.image.port.FashionImageProviderPort;
import com.ruoyi.fashion.application.agent.run.FashionRuntimeException;

/** 安全默认适配器；没有经过 IMP-10 放行时不会产生真实外部调用。 */
public final class DisabledFashionImageProviderAdapter implements FashionImageProviderPort {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public ProviderResult submit(String requestKey, String imageType, JsonNode inputs, JsonNode parameters, int count) {
        throw new FashionRuntimeException("PROVIDER_DISABLED", "图片 Provider 未启用", false);
    }

    @Override
    public ProviderResult query(String providerTaskId) {
        throw new FashionRuntimeException("PROVIDER_DISABLED", "图片 Provider 未启用", false);
    }
}
