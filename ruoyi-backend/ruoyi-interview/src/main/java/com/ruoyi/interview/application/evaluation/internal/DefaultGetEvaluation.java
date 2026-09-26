package com.ruoyi.interview.application.evaluation.internal;

import com.ruoyi.interview.application.evaluation.EvaluationRepository;
import com.ruoyi.interview.application.evaluation.EvaluationStreamCursorPort;
import com.ruoyi.interview.application.evaluation.GetEvaluation;
import com.ruoyi.interview.application.shared.ApplicationErrorCode;
import com.ruoyi.interview.application.shared.ApplicationException;

import java.util.Map;

public final class DefaultGetEvaluation implements GetEvaluation {

    private final EvaluationRepository repository;
    private final EvaluationStreamCursorPort cursors;

    public DefaultGetEvaluation(EvaluationRepository repository, EvaluationStreamCursorPort cursors) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.cursors = java.util.Objects.requireNonNull(cursors);
    }

    @Override
    public Result handle(Query query) {
        var principal = query.context().principal();
        var evaluation = repository.findRun(principal.tenantId(), query.evaluationId())
                .orElseThrow(DefaultGetEvaluation::notFound);
        if (!evaluation.userId().equals(principal.userId())) {
            throw notFound();
        }
        return new Result(evaluation, cursors.current(principal.tenantId(), evaluation.id()));
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "evaluation was not found", false, Map.of());
    }
}
