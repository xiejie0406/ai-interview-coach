package com.ruoyi.aps.application.foundation;

import java.util.function.Supplier;

/**
 * 应用层事务边界；具体事务管理器由持久化适配器提供。
 */
public interface ApsTransactionOperations
{
    <T> T required(Supplier<T> action);

    /** 发布等需要阻止事实表并发写入的短事务；不支持独立隔离级别的适配器仍可保守回退。 */
    default <T> T serializable(Supplier<T> action) { return required(action); }

    default void required(Runnable action)
    {
        required(() -> {
            action.run();
            return null;
        });
    }
}
