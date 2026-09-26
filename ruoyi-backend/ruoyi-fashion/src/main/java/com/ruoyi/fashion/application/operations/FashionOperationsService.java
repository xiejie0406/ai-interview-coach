package com.ruoyi.fashion.application.operations;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.operations.FashionOperationsSnapshot.AlertCandidate;
import com.ruoyi.fashion.application.operations.FashionOperationsSnapshot.Metric;
import com.ruoyi.fashion.application.operations.port.FashionOperationsRepository;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.stereotype.Service;

@FashionModuleEnabled
@Service
public final class FashionOperationsService {
    private final FashionOperationsRepository repository;
    private final FashionTimeSource time;
    private final ISysConfigService settings;

    public FashionOperationsService(FashionOperationsRepository repository,
            FashionTimeSource time, ISysConfigService settings) {
        this.repository = repository;
        this.time = time;
        this.settings = settings;
    }

    public FashionOperationsSnapshot overview(int limit) {
        Instant now = time.now();
        Instant since = now.minus(Duration.ofHours(24));
        int freshnessHours = integer("fashion.stock.freshnessHours", 24, 1, 168);
        long imports = repository.countImports(since);
        long failedImports = repository.countFailedImports(since);
        long imageIssues = repository.countImageFailedOrUnknown();
        long expiredStock = repository.countExpiredStock(now.minus(Duration.ofHours(freshnessHours)));
        long deliveries = repository.countDeliveryTasks(since);
        long failedDeliveries = repository.countFailedDeliveryTasks(since);
        BigDecimal importRate = percent(failedImports, imports);
        BigDecimal deliveryRate = percent(failedDeliveries, deliveries);
        BigDecimal importThreshold = decimal("fashion.alert.importFailurePercent", "10.00");
        int imageThreshold = integer("fashion.alert.imagePendingCount", 10, 1, 100000);
        int stockThreshold = integer("fashion.alert.stockExpiredCount", 50, 1, 10000000);
        BigDecimal deliveryThreshold = decimal("fashion.alert.deliveryFailurePercent", "10.00");
        List<AlertCandidate> alerts = new ArrayList<>();
        alerts.add(alert("import_failure_rate", importRate.compareTo(importThreshold) >= 0,
                importRate.toPlainString(), importThreshold.toPlainString(), "24 小时批次失败率达到阈值"));
        alerts.add(alert("image_failed_or_unknown", imageIssues >= imageThreshold,
                Long.toString(imageIssues), Integer.toString(imageThreshold), "图片失败或待查数量达到阈值"));
        alerts.add(alert("stock_expired", expiredStock >= stockThreshold,
                Long.toString(expiredStock), Integer.toString(stockThreshold), "库存过期数量达到阈值"));
        alerts.add(alert("delivery_failure_rate", deliveryRate.compareTo(deliveryThreshold) >= 0,
                deliveryRate.toPlainString(), deliveryThreshold.toPlainString(), "24 小时文件失败率达到阈值"));
        return new FashionOperationsSnapshot(now,
                metric("import_failure_rate", importRate, "%", "24h"),
                metric("image_failed_or_unknown", BigDecimal.valueOf(imageIssues), "count", "current"),
                metric("stock_expired", BigDecimal.valueOf(expiredStock), "count", freshnessHours + "h"),
                metric("delivery_failure_rate", deliveryRate, "%", "24h"),
                metric("delivery_average_duration", BigDecimal.valueOf(repository.averageDeliveryDurationMs(since))
                        .setScale(2, RoundingMode.HALF_UP), "ms", "24h"),
                List.copyOf(alerts), repository.exceptions(now, limit),
                "not_configured（仅形成告警候选，未声称通知已送达）");
    }

    public RetentionDryRun retentionDryRun() {
        Instant now = time.now();
        int quoteDays = integer("fashion.retention.quoteDays", 365, 30, 3650);
        int importDays = integer("fashion.retention.importFileDays", 90, 7, 3650);
        int imageDays = integer("fashion.retention.failedImageDays", 30, 7, 3650);
        return new RetentionDryRun(now, false, quoteDays, importDays, imageDays,
                repository.retentionCandidates(now, now.minus(Duration.ofDays(quoteDays)),
                        now.minus(Duration.ofDays(importDays)), now.minus(Duration.ofDays(imageDays))));
    }

    private int integer(String key, int fallback, int min, int max) {
        String raw = settings.selectConfigByKey(key);
        try {
            int value = Integer.parseInt(raw == null || raw.isBlank() ? Integer.toString(fallback) : raw);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ServiceException("Fashion 运维配置无效：" + key);
        }
    }

    private BigDecimal decimal(String key, String fallback) {
        String raw = settings.selectConfigByKey(key);
        try {
            BigDecimal value = new BigDecimal(raw == null || raw.isBlank() ? fallback : raw).setScale(2);
            if (value.signum() < 0 || value.compareTo(new BigDecimal("100.00")) > 0) throw new ArithmeticException();
            return value;
        } catch (RuntimeException exception) {
            throw new ServiceException("Fashion 运维配置无效：" + key);
        }
    }

    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static Metric metric(String code, BigDecimal value, String unit, String window) {
        return new Metric(code, value.toPlainString(), unit, window);
    }

    private static AlertCandidate alert(String code, boolean reached, String current,
            String threshold, String description) {
        return new AlertCandidate(code, reached, current, threshold, description);
    }
}
