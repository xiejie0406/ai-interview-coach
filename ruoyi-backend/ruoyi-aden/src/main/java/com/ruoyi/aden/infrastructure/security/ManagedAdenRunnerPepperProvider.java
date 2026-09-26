package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.application.runner.AdenRunnerPepperProvider;
import com.ruoyi.system.secret.ManagedSecretService;
import java.util.Base64;
import tools.jackson.databind.ObjectMapper;

/** Runner pepper 的唯一运行时来源；保留 previous 用途以验证旧凭据和会话。 */
public final class ManagedAdenRunnerPepperProvider implements AdenRunnerPepperProvider {
    private static final String ACTIVE = "platform.aden.runner-pepper";
    private static final String PREVIOUS = "platform.aden.runner-pepper.previous";
    private final ManagedSecretService secrets;
    private final ObjectMapper json;

    public ManagedAdenRunnerPepperProvider(ManagedSecretService secrets, ObjectMapper json) {
        this.secrets = secrets;
        this.json = json;
        read(ACTIVE);
    }

    @Override public String currentKeyId() { return read(ACTIVE).id(); }

    @Override public byte[] pepper(String keyId) {
        Key active = read(ACTIVE);
        if (active.id().equals(keyId)) return active.bytes().clone();
        Key previous = read(PREVIOUS);
        if (previous.id().equals(keyId)) return previous.bytes().clone();
        throw new IllegalStateException("未知 Runner pepper key id");
    }

    private Key read(String alias) {
        try {
            var value = json.readTree(secrets.require("PLATFORM", alias));
            String id = value.path("keyId").asText();
            String encoded = value.path("keyBase64").asText();
            if (id.isBlank() || id.length() > 64 || encoded.isBlank())
                throw new IllegalStateException("Runner pepper 密钥组不完整");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 32) throw new IllegalStateException("Runner pepper 至少 32 字节");
            return new Key(id, bytes);
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Runner pepper 密钥组格式无效");
        }
    }

    private record Key(String id, byte[] bytes) { }
}
