package com.ruoyi.aden.application.task;

import com.ruoyi.aden.application.reliability.AdenIdempotentTransactionRunner;

import java.util.Objects;

/** Task 命令门面：每次重试均从完整 Application Command 重新开始。 */
public final class AdenTaskCommandService {
    private final AdenTaskTransactionService transactions;
    private final AdenIdempotentTransactionRunner transactionRunner;

    public AdenTaskCommandService(AdenTaskTransactionService transactions,
                                  AdenIdempotentTransactionRunner transactionRunner) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
    }

    public AdenTaskResult createTask(AdenTaskTransactionService.CreateTask command) {
        Objects.requireNonNull(command, "command");
        return transactionRunner.execute(command.idempotencyKey(), () -> transactions.createTask(command));
    }

    public AdenTaskResult submitForValidation(AdenTaskTransactionService.SubmitForValidation command) {
        Objects.requireNonNull(command, "command");
        return transactionRunner.execute(
                command.idempotencyKey(), () -> transactions.submitForValidation(command));
    }

    public AdenTaskResult requestCancel(AdenTaskTransactionService.RequestCancel command) {
        Objects.requireNonNull(command, "command");
        return transactionRunner.execute(
                command.idempotencyKey(), () -> transactions.requestCancel(command));
    }
}
