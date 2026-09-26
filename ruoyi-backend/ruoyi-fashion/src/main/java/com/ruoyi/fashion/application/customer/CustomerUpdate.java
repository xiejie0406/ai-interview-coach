package com.ruoyi.fashion.application.customer;

import java.util.List;

public record CustomerUpdate(
        String name,
        String customerType,
        String contactName,
        String contactPhone,
        String region,
        Long salespersonId,
        List<Long> collaboratorIds,
        String internalNote,
        long rowVersion) {
}
