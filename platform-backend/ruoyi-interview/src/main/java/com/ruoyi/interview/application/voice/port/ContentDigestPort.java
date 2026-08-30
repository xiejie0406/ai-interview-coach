package com.ruoyi.interview.application.voice.port;

@FunctionalInterface
public interface ContentDigestPort {
    /** 纯本地、确定性摘要；不得保留 confidentialText，也不得在事务内发起网络调用。 */
    String digest(String confidentialText);
}
