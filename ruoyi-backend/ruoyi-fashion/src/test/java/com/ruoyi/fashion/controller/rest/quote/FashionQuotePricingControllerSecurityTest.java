package com.ruoyi.fashion.controller.rest.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import com.ruoyi.common.annotation.Log;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class FashionQuotePricingControllerSecurityTest {
    @Test
    void pricingEndpointsSeparateReadEditApprovalAndConfirmationPermissions() throws Exception {
        assertPermission(FashionQuotePricingController.class.getMethod("get", String.class),
                "fashion:quote:query", false);
        assertPermission(FashionQuotePricingController.class.getMethod("save", String.class,
                QuotePricingRequest.class), "fashion:quote:edit", true);
        assertPermission(FashionQuotePricingController.class.getMethod("requestApproval", String.class,
                QuoteApprovalRequest.class), "fashion:quote:edit", true);
        assertPermission(FashionQuotePricingController.class.getMethod("approve", String.class,
                QuoteApprovalRequest.class), "fashion:quote:approve", true);
        assertPermission(FashionQuotePricingController.class.getMethod("confirm", String.class,
                QuoteConfirmRequest.class), "fashion:quote:confirm", true);
    }

    private static void assertPermission(Method method, String permission, boolean audited) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('" + permission + "')", authorization.value());
        if (audited) assertNotNull(method.getAnnotation(Log.class));
    }
}
