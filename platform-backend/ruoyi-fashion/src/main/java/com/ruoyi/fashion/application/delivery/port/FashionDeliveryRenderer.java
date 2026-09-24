package com.ruoyi.fashion.application.delivery.port;

import java.util.List;

import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;
import com.ruoyi.fashion.application.delivery.GeneratedDeliveryArtifact;

public interface FashionDeliveryRenderer {
    String fileType();
    List<GeneratedDeliveryArtifact> render(DeliveryQuoteSnapshot snapshot);
}
