package com.ruoyi.fashion.application.delivery;

import java.util.List;

public record DeliveryWorkspace(
        String quoteId, String quoteNo, int versionNo, String quoteTitle, String quoteHash,
        String status, List<DeliveryFileView> files) {
}
