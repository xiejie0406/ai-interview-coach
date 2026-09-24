package com.ruoyi.fashion.application.image;

import com.ruoyi.fashion.configuration.FashionImageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fashion.image", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class FashionQuoteImageWorker {
    private final FashionQuoteImageService service;
    private final FashionImageProperties properties;

    public FashionQuoteImageWorker(FashionQuoteImageService service, FashionImageProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fashion.image.poll-delay:PT2S}")
    public void poll() {
        for (int count = 0; count < 4 && service.processNext("image-worker"); count++) {
            // 每轮有界处理，避免图片任务独占调度线程。
        }
    }
}
