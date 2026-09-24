package com.ruoyi.aden.application.runner;

/** Pepper 不进入数据库；按 key id 从运行环境解析并返回防御性副本。 */
public interface AdenRunnerPepperProvider {
    String currentKeyId();
    byte[] pepper(String keyId);
}
