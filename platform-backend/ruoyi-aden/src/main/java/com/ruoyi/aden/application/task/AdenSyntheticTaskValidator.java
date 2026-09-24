package com.ruoyi.aden.application.task;

import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenTaskType;

import java.util.Objects;

/** 当前合成 Validator：纯确定性、零网络、零文件、零 Provider。 */
public final class AdenSyntheticTaskValidator {
    public ValidationResult validate(AdenTaskType taskType,
                                     AdenCapabilityCode capability,
                                     AdenSyntheticTaskInput input) {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(input, "input");
        if (taskType != AdenTaskType.SYNTHETIC_CORE || capability != AdenCapabilityCode.CORE) {
            return ValidationResult.failure("ADEN_UNSUPPORTED_SYNTHETIC_TASK");
        }
        if (input.expectedOutcome() == AdenSyntheticTaskInput.ExpectedOutcome.FAIL_VALIDATION) {
            return ValidationResult.failure("SYNTHETIC_VALIDATION_REJECTED");
        }
        return ValidationResult.success();
    }

    public record ValidationResult(boolean passed, String reasonCode) {
        public static ValidationResult success() { return new ValidationResult(true, null); }
        public static ValidationResult failure(String reasonCode) {
            return new ValidationResult(false, Objects.requireNonNull(reasonCode, "reasonCode"));
        }
    }
}
