package com.aiinterviewcoach.application.identity;

import com.aiinterviewcoach.domain.platform.CorrelationId;
import com.aiinterviewcoach.domain.platform.DomainPreconditions;

/** 每个请求、SSE 和 WebSocket 建连都重新解析服务端 session。 */
@FunctionalInterface
public interface ResolvePrincipal {

    ResolvedPrincipal handle(Query query);

    record Query(String sessionToken, CorrelationId correlationId) {
        public Query {
            sessionToken = DomainPreconditions.requireText(sessionToken, "sessionToken");
            DomainPreconditions.requireNonNull(correlationId, "correlationId");
        }

        @Override
        public String toString() {
            return "Query[sessionToken=<redacted>, correlationId=" + correlationId + "]";
        }
    }
}
