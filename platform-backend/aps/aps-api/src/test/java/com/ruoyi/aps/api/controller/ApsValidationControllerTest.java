package com.ruoyi.aps.api.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.solver.contract.Problem;
import com.ruoyi.aps.solver.contract.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApsValidationControllerTest
{
    private final ApsValidationController controller = new ApsValidationController(
            Clock.fixed(Instant.parse("2026-09-13T02:03:04.567Z"), ZoneOffset.UTC));

    @Test
    void rejectsUnsupportedSameStartGoldenInputDeterministically() throws Exception
    {
        String body = Files.readString(findWorkspaceRoot()
                .resolve("contracts/aps/examples/golden/valid-same-start-input.json"));

        ValidationResult result = controller.validate("validation-same-start-001", body);

        assertThat(result.validationStatus()).isEqualTo(ValidationResult.Status.FAIL);
        assertThat(result.publishable()).isFalse();
        assertThat(result.validatedAt()).isEqualTo(Instant.parse("2026-09-13T02:03:04.567Z"));
        assertThat(result.problems()).extracting(Problem::reasonCode)
                .contains(Problem.ReasonCode.UNSUPPORTED_SYNC_RULE);
    }

    @Test
    void mapsMalformedOrNullJsonToStableInvalidRequest()
    {
        assertThatThrownBy(() -> controller.validate("validation-invalid-001", "null"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(exception -> assertThat(((ApsBusinessException) exception).errorCode())
                        .isEqualTo(ApsErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> controller.validate("validation-invalid-002", "{"))
                .isInstanceOf(ApsBusinessException.class)
                .satisfies(exception -> assertThat(((ApsBusinessException) exception).errorCode())
                        .isEqualTo(ApsErrorCode.INVALID_REQUEST));
    }

    @Test
    void endpointRequiresPlanningValidatePermission() throws Exception
    {
        PreAuthorize authorization = ApsValidationController.class
                .getMethod("validate", String.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("@ss.hasPermi('aps:planning:validate')");
    }

    private Path findWorkspaceRoot()
    {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null)
        {
            if (Files.isRegularFile(cursor.resolve("contracts/aps/contract-test-manifest.json"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 contracts/aps");
    }
}
