package com.aiinterviewcoach.application.platform.internal;

/** 服务端内部摘要工具；摘要不是授权，也不应被当作正文或 Secret。 */
@Deprecated(forRemoval = true)
public final class ServerSideDigest {

    private ServerSideDigest() {
    }

    public static String sha256(String... parts) {
        return com.aiinterviewcoach.application.platform.ServerSideDigest.sha256(parts);
    }
}
