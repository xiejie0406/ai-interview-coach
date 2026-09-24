package com.ruoyi.aden.application.reliability;

import com.ruoyi.aden.application.idempotency.AdenIdempotencyKey;

import java.util.function.Supplier;

/** 只有携带幂等键的命令才允许进行整事务重试。 */
public interface AdenIdempotentTransactionRunner {
    <T> T execute(AdenIdempotencyKey idempotencyKey, Supplier<T> wholeCommand);
}
