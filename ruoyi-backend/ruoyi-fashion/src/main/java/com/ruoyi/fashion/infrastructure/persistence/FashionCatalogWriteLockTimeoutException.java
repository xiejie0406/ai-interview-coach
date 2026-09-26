package com.ruoyi.fashion.infrastructure.persistence;

/** 未在规定时间取得 catalog-write 锁；调用方不得继续写当前事实。 */
public final class FashionCatalogWriteLockTimeoutException extends RuntimeException {
    public FashionCatalogWriteLockTimeoutException(String lockName, int timeoutSeconds) {
        super("等待 Fashion catalog-write 锁超时：lock=" + lockName + ", timeoutSeconds=" + timeoutSeconds);
    }
}
