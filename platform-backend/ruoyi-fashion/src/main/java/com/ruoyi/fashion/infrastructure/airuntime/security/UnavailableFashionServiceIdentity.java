package com.ruoyi.fashion.infrastructure.airuntime.security;

/** 未注入 Secret 时保持应用可启动，但任何内部签名或验签操作都会拒绝。 */
public final class UnavailableFashionServiceIdentity implements FashionServiceIdentity {

    @Override
    public FashionServiceAuthHeaders sign(FashionServiceRequest request) {
        throw unavailable();
    }

    @Override
    public FashionServicePrincipal verify(
            FashionServiceRequest request,
            FashionServiceAuthHeaders headers) {
        throw unavailable();
    }

    @Override
    public boolean configured() {
        return false;
    }

    private FashionServiceAuthenticationException unavailable() {
        return new FashionServiceAuthenticationException(FashionServiceAuthFailure.NOT_CONFIGURED);
    }
}
