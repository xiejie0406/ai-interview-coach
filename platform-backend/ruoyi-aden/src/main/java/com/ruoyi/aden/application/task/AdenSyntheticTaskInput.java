package com.ruoyi.aden.application.task;

import java.util.Objects;
import java.util.regex.Pattern;

public record AdenSyntheticTaskInput(
        String fixtureId,
        String instruction,
        ExpectedOutcome expectedOutcome) {
    private static final Pattern FIXTURE = Pattern.compile("fixture:[a-z0-9][a-z0-9._-]{2,63}");

    public AdenSyntheticTaskInput {
        Objects.requireNonNull(fixtureId, "fixtureId");
        Objects.requireNonNull(instruction, "instruction");
        if (!FIXTURE.matcher(fixtureId).matches()) throw new IllegalArgumentException("fixtureId 不合法");
        String normalized = instruction.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new IllegalArgumentException("instruction 必须为 1..1000 个字符");
        }
        instruction = normalized;
        expectedOutcome = expectedOutcome == null ? ExpectedOutcome.SUCCEED : expectedOutcome;
    }

    public enum ExpectedOutcome {
        SUCCEED,
        FAIL_VALIDATION,
        CANCEL_AT_SAFE_POINT
    }
}
