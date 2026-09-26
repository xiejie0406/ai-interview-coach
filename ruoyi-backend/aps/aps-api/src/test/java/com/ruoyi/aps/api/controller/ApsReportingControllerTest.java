package com.ruoyi.aps.api.controller;

import java.lang.reflect.Method;
import java.time.LocalDate;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.reporting.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApsReportingControllerTest
{
    @Test
    void separatesViewAndExportPermissions() throws Exception
    {
        assertPermission("dailyProduction", "@ss.hasPermi('aps:report:view')", LocalDate.class,
                String.class, String.class);
        assertPermission("laborCapacity", "@ss.hasPermi('aps:report:view')", LocalDate.class,
                LocalDate.class, String.class, String.class, String.class);
        assertPermission("orderDelivery", "@ss.hasPermi('aps:report:view')", String.class);
        assertPermission("exportDailyProduction", "@ss.hasPermi('aps:report:export')", LocalDate.class,
                String.class, String.class);
        assertPermission("exportLaborCapacity", "@ss.hasPermi('aps:report:export')", LocalDate.class,
                LocalDate.class, String.class, String.class, String.class);
        assertPermission("exportOrderDelivery", "@ss.hasPermi('aps:report:export')", String.class);
    }

    @Test
    void csvEscapesFormulaPrefixQuotesAndLineBreaks() throws Exception
    {
        ApsReportingController controller = new ApsReportingController(mock(ReportingService.class),
                mock(ApsAuditActorProvider.class));
        Method method = ApsReportingController.class.getDeclaredMethod("csvCell", Object.class);
        method.setAccessible(true);

        assertThat(method.invoke(controller, "=HYPERLINK(\"bad\")\r\nnext"))
                .isEqualTo("\"'=HYPERLINK(\"\"bad\"\")\r\nnext\"");
        assertThat(method.invoke(controller, "+1")).isEqualTo("\"'+1\"");
        assertThat(method.invoke(controller, "safe")).isEqualTo("\"safe\"");
    }

    private void assertPermission(String method, String expected, Class<?>... parameters) throws Exception
    {
        PreAuthorize annotation = ApsReportingController.class.getMethod(method, parameters)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
