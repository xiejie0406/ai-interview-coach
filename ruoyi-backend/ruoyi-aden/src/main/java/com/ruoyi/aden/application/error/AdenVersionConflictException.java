package com.ruoyi.aden.application.error;

import java.util.Map;

public final class AdenVersionConflictException extends AdenApplicationException {
    public AdenVersionConflictException(String expectedVersion, String expectedState) {
        super("ADEN_VERSION_CONFLICT", "任务版本或状态已变化",
                Map.of("expectedVersion", expectedVersion, "expectedState", expectedState));
    }
}
