package com.ruoyi.aps.solver.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 可展示、可定位且不携带内部堆栈的稳定业务问题。 */
public record Problem(String schemaVersion, String contractType, String problemId, ReasonCode reasonCode,
        String constraintCode, Severity severity, String title, String detail, boolean retryable,
        List<ObjectRef> objectRefs, TimeRange timeRange, Map<String, Object> measurements)
{
    public Problem
    {
        objectRefs = objectRefs == null ? List.of() : List.copyOf(objectRefs);
        measurements = measurements == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(measurements));
    }
    public enum Severity { INFO, WARNING, ERROR }
    public enum ReasonCode {
        NO_ROUTE, MISSING_DURATION, NO_QUALIFIED_RESOURCE, NO_COMMON_WINDOW, RESOURCE_OVERLAP,
        CAPACITY_EXCEEDED, PRECEDENCE_VIOLATION, QUANTITY_NOT_RELEASED, BATCH_INCOMPATIBLE,
        LOCK_CONFLICT, OPEN_OCCUPANCY_UNKNOWN_RELEASE, STALE_INPUT, UNSUPPORTED_SYNC_RULE,
        TIME_LIMIT_NO_SOLUTION, INVALID_INTERVAL, INVALID_REVISION, HASH_MISMATCH,
        SOLVER_MODEL_INVALID, UNAUTHORIZED, NOT_FOUND, CONFLICT, STALE_VERSION,
        IDEMPOTENCY_CONFLICT, CONTRACT_VALIDATION_FAILED, CAPABILITY_NOT_IMPLEMENTED,
        SERVICE_UNAVAILABLE, INTERNAL_ERROR
    }
    public record ObjectRef(String objectType, String objectId, String field) { }
    public record TimeRange(Instant startAt, Instant endAt) { }
}
