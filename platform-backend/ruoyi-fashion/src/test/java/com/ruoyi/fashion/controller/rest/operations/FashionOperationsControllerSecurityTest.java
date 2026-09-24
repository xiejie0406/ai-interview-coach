package com.ruoyi.fashion.controller.rest.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class FashionOperationsControllerSecurityTest {
    @Test
    void operationsAndRetentionHaveIndependentPermissions() throws Exception {
        PreAuthorize overview = FashionOperationsController.class.getMethod("overview", int.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize retention = FashionOperationsController.class.getMethod("retentionDryRun")
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermi('fashion:operations:query')", overview.value());
        assertEquals("@ss.hasPermi('fashion:operations:retention')", retention.value());
    }
}
