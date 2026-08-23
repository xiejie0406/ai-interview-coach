package com.aiinterviewcoach.application.shared;

import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;
import com.aiinterviewcoach.domain.platform.PrincipalRef;

import java.time.Instant;

/** 受保护查询同样显式携带 tenant-aware principal。 */
public record QueryContext(PrincipalRef principal, CorrelationId correlationId, Instant requestedAt) {

    public QueryContext {
        DomainPreconditions.requireNonNull(principal, "principal");
        DomainPreconditions.requireNonNull(correlationId, "correlationId");
        DomainPreconditions.requireNonNull(requestedAt, "requestedAt");
    }
}
