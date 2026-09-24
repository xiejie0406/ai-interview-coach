package com.ruoyi.aden.application.runner;

import java.time.Instant;
import java.util.Optional;

/** Runner token 只由公开 id 定位，并在端口实现中做 keyed digest 常量时间校验。 */
public interface AdenRunnerAuthenticationPort {
    Optional<AdenRunnerCredentialPrincipal> authenticateCredential(String token, Instant now);
    Optional<AdenRunnerSessionPrincipal> authenticateSession(String token, Instant now);
}
