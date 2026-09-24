package com.ruoyi.aden.infrastructure.security;

import com.ruoyi.aden.application.runner.AdenRunnerPepperProvider;

import java.util.Base64;
import java.util.Objects;

/** 当前 key id 与 Base64 pepper 只来自环境变量，不进入 Spring 配置回显。 */
public final class EnvironmentAdenRunnerPepperProvider implements AdenRunnerPepperProvider {
    public static final String KEY_ID_ENV = "ADEN_RUNNER_PEPPER_KEY_ID";
    public static final String PEPPER_ENV = "ADEN_RUNNER_PEPPER_BASE64";
    private final String keyId;
    private final byte[] pepper;

    public EnvironmentAdenRunnerPepperProvider(String keyId, String encodedPepper) {
        this.keyId=Objects.requireNonNull(keyId,"Runner pepper key id 缺失").trim();
        if(this.keyId.isEmpty()||this.keyId.length()>64)throw new IllegalStateException("Runner pepper key id 非法");
        try{this.pepper=Base64.getDecoder().decode(Objects.requireNonNull(encodedPepper,"Runner pepper 缺失"));}
        catch(IllegalArgumentException e){throw new IllegalStateException("Runner pepper 不是合法 Base64",e);}
        if(this.pepper.length<32)throw new IllegalStateException("Runner pepper 至少 32 字节");
    }

    public static EnvironmentAdenRunnerPepperProvider fromEnvironment(){return new EnvironmentAdenRunnerPepperProvider(System.getenv(KEY_ID_ENV),System.getenv(PEPPER_ENV));}
    @Override public String currentKeyId(){return keyId;}
    @Override public byte[] pepper(String requestedKeyId){if(!keyId.equals(requestedKeyId))throw new IllegalStateException("未知 Runner pepper key id");return pepper.clone();}
}
