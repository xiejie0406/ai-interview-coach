package com.ruoyi.fashion.application.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.ruoyi.fashion.application.operations.port.FashionOperationsRepository;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.Test;

class FashionOperationsServiceTest {
    @Test
    void calculatesThresholdCandidatesWithoutClaimingNotificationDelivery() {
        FashionOperationsRepository repository = mock(FashionOperationsRepository.class);
        when(repository.countImports(any())).thenReturn(20L);
        when(repository.countFailedImports(any())).thenReturn(3L);
        when(repository.countImageFailedOrUnknown()).thenReturn(11L);
        when(repository.countExpiredStock(any())).thenReturn(2L);
        when(repository.countDeliveryTasks(any())).thenReturn(10L);
        when(repository.countFailedDeliveryTasks(any())).thenReturn(1L);
        when(repository.averageDeliveryDurationMs(any())).thenReturn(1234.56);
        when(repository.exceptions(any(), anyInt())).thenReturn(List.of());
        FashionTimeSource time = mock(FashionTimeSource.class);
        when(time.now()).thenReturn(Instant.parse("2026-09-13T00:00:00Z"));
        ISysConfigService settings = mock(ISysConfigService.class);

        FashionOperationsSnapshot result = new FashionOperationsService(repository, time, settings).overview(50);

        assertThat(result.importFailureRate().value()).isEqualTo("15.00");
        assertThat(result.deliveryFailureRate().value()).isEqualTo("10.00");
        assertThat(result.alertCandidates()).extracting(FashionOperationsSnapshot.AlertCandidate::thresholdReached)
                .containsExactly(true, true, false, true);
        assertThat(result.notificationStatus()).contains("not_configured", "未声称通知已送达");
    }

    @Test
    void retentionIsAlwaysDryRunAndKeepsRepositoryDecisionsExplainable() {
        FashionOperationsRepository repository = mock(FashionOperationsRepository.class);
        RetentionCandidate protectedItem = new RetentionCandidate("quote_file", "1", "delivery/a.pptx",
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                "protected", "报价仍在有效期内", "100");
        when(repository.retentionCandidates(any(), any(), any(), any())).thenReturn(List.of(protectedItem));
        FashionTimeSource time = mock(FashionTimeSource.class);
        when(time.now()).thenReturn(Instant.parse("2026-09-13T00:00:00Z"));
        ISysConfigService settings = mock(ISysConfigService.class);

        RetentionDryRun result = new FashionOperationsService(repository, time, settings).retentionDryRun();

        assertThat(result.deletionEnabled()).isFalse();
        assertThat(result.quoteDays()).isEqualTo(365);
        assertThat(result.importFileDays()).isEqualTo(90);
        assertThat(result.failedImageDays()).isEqualTo(30);
        assertThat(result.candidates()).containsExactly(protectedItem);
    }
}
