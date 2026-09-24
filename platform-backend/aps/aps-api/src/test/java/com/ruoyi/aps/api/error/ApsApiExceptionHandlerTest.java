package com.ruoyi.aps.api.error;

import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.application.foundation.ApsValidationException;
import com.ruoyi.aps.application.foundation.ApsValidationIssue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApsApiExceptionHandlerTest
{
    private final ApsApiExceptionHandler handler = new ApsApiExceptionHandler();

    @Test
    void mapsBusinessFailureWithoutLeakingImplementationDetails()
    {
        var response = handler.handleBusiness(
                new ApsBusinessException(ApsErrorCode.STALE_VERSION, "计划版本已过期"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reasonCode()).isEqualTo("STALE_VERSION");
        assertThat(response.getBody().problemId()).matches("[0-9a-f-]{36}");
    }

    @Test
    void hidesUnexpectedExceptionText()
    {
        var response = handler.handleUnexpected(new IllegalStateException("jdbc:mysql://secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().detail()).doesNotContain("secret");
    }

    @Test
    void keepsObjectReferencesForRouteValidation()
    {
        var response = handler.handleBusiness(new ApsValidationException(ApsErrorCode.UNSUPPORTED_SYNC_RULE,
                "路线未通过发布校验", List.of(new ApsValidationIssue("UNSUPPORTED_SYNC_RULE", "ROUTE_EDGE",
                        "00000000-0000-4000-8000-000000000001", "dependencyType", "暂不支持"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().reasonCode()).isEqualTo("UNSUPPORTED_SYNC_RULE");
        assertThat(response.getBody().objectRefs()).hasSize(1);
    }

    @Test
    void exposesStableExecutionReasonCodesAsConflicts()
    {
        var response = handler.handleBusiness(new ApsBusinessException(
                ApsErrorCode.INVALID_EXECUTION_TRANSITION, "运行中不得直接取消"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().reasonCode()).isEqualTo("INVALID_EXECUTION_TRANSITION");
    }
}
