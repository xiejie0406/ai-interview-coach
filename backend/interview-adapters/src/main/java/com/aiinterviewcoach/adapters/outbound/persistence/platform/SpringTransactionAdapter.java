package com.aiinterviewcoach.adapters.outbound.persistence.platform;

import com.aiinterviewcoach.application.platform.port.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public final class SpringTransactionAdapter implements TransactionPort {

    private final TransactionTemplate required;

    public SpringTransactionAdapter(PlatformTransactionManager transactionManager) {
        required = new TransactionTemplate(transactionManager);
        required.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        return required.execute(status -> work.get());
    }
}

