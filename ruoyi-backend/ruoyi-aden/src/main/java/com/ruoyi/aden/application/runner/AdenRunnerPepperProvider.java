package com.ruoyi.aden.application.runner;

/** Pepper 明文不进入 Aden 业务表；按 key id 从平台密钥模块解析并返回防御性副本。 */
public interface AdenRunnerPepperProvider {
    String currentKeyId();
    byte[] pepper(String keyId);
}
