package com.ruoyi.fashion.controller.rest.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import com.ruoyi.common.annotation.Log;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class FashionDeliveryControllerSecurityTest {
    @Test
    void deliveryEndpointsReauthorizeReadExportDownloadAndRetention() throws Exception {
        assertPermission(FashionDeliveryController.class.getMethod("workspace", String.class),
                "fashion:quote:query", false);
        assertPermission(FashionDeliveryController.class.getMethod("request", String.class, DeliveryFileRequest.class),
                "fashion:quote:export", true);
        assertPermission(FashionDeliveryController.class.getMethod("retry", String.class, String.class, long.class),
                "fashion:quote:export", true);
        assertPermission(FashionDeliveryController.class.getMethod("download", String.class, String.class, int.class),
                "fashion:quote:export", true);
        assertPermission(FashionDeliveryController.class.getMethod("extendRetention", String.class, String.class,
                RetentionExtensionRequest.class), "fashion:operations:retention", true);
    }

    private static void assertPermission(Method method, String permission, boolean audited) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('" + permission + "')", authorization.value());
        if (audited) assertNotNull(method.getAnnotation(Log.class));
    }
}
