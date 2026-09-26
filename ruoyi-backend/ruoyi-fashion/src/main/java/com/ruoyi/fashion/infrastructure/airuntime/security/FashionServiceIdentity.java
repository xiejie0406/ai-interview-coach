package com.ruoyi.fashion.infrastructure.airuntime.security;

/** Java/Python 双向服务身份边界。 */
public interface FashionServiceIdentity {

    FashionServiceAuthHeaders sign(FashionServiceRequest request);

    FashionServicePrincipal verify(FashionServiceRequest request, FashionServiceAuthHeaders headers);

    boolean configured();
}
