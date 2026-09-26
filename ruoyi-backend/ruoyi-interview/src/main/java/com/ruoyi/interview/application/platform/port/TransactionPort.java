package com.ruoyi.interview.application.platform.port;

import java.util.function.Supplier;

/** 应用用例声明本地事务；端口实现不得在事务闭包内进行网络调用。 */
public interface TransactionPort {

    <T> T required(Supplier<T> work);

    default void required(Runnable work) {
        required(() -> {
            work.run();
            return null;
        });
    }
}
