package com.ruoyi.aps.infrastructure.mysql.configuration;

import java.util.Objects;
import java.util.function.Supplier;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

final class SpringApsTransactionOperations implements ApsTransactionOperations
{
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate serializableTemplate;

    SpringApsTransactionOperations(TransactionTemplate transactionTemplate)
    {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.serializableTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.serializableTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public <T> T required(Supplier<T> action)
    {
        Objects.requireNonNull(action, "action");
        return transactionTemplate.execute(status -> action.get());
    }

    @Override
    public <T> T serializable(Supplier<T> action)
    {
        Objects.requireNonNull(action, "action");
        return serializableTemplate.execute(status -> action.get());
    }
}
