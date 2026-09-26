package com.ruoyi.fashion.application.delivery.worker;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import com.ruoyi.fashion.application.delivery.FashionDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@FashionModuleEnabled
@Component
@ConditionalOnProperty(prefix = "fashion.delivery", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public final class FashionDeliveryWorker {
    private final FashionDeliveryService service;

    public FashionDeliveryWorker(FashionDeliveryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${fashion.delivery.poll-delay:PT2S}")
    public void poll() {
        for (int count = 0; count < 4 && service.processNext("delivery-worker"); count++) {
            // 每轮有界处理，避免大文件生成长期占用调度线程。
        }
    }
}
