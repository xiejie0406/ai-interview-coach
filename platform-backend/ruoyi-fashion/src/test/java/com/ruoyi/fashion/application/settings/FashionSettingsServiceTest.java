package com.ruoyi.fashion.application.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.security.FashionDictionaryGuard;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FashionSettingsServiceTest {
    private ISysConfigService configService;
    private FashionDictionaryGuard dictionaryGuard;
    private FashionSettingsService service;

    @BeforeEach
    void setUp() {
        configService = mock(ISysConfigService.class);
        dictionaryGuard = mock(FashionDictionaryGuard.class);
        service = new FashionSettingsService(new FashionSettingRegistry(), configService, dictionaryGuard);
    }

    @Test
    void listsOnlyTheClosedNonSecretRegistry() {
        List<FashionSettingView> settings = service.list();

        assertEquals(15, settings.size());
        assertEquals("24", settings.stream()
                .filter(item -> item.key().equals("fashion.stock.freshnessHours"))
                .findFirst().orElseThrow().value());
    }

    @Test
    void rejectsUnknownKeyAndOutOfRangeValue() {
        assertThrows(ServiceException.class, () -> service.update("fashion.provider.secret", "secret", "tester"));
        assertThrows(ServiceException.class,
                () -> service.update("fashion.stock.freshnessHours", "0", "tester"));
        assertThrows(ServiceException.class,
                () -> service.update("fashion.ai.monthlyBudgetCny", "1e3", "tester"));
    }

    @Test
    void validatesDictionaryAndReturnsBeforeAfter() {
        SysConfig existing = new SysConfig();
        existing.setConfigId(101L);
        existing.setConfigKey("fashion.catalog.defaultWarehouse");
        existing.setConfigValue("MAIN");
        when(configService.selectConfigList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(existing));
        when(configService.updateConfig(existing)).thenReturn(1);

        FashionSettingChange change = service.update(
                "fashion.catalog.defaultWarehouse", "EAST", "tester");

        verify(dictionaryGuard).requireActiveValue("fashion_warehouse", "EAST");
        assertEquals(new FashionSettingChange(
                "fashion.catalog.defaultWarehouse", "MAIN", "EAST", "tester"), change);
        assertEquals("EAST", existing.getConfigValue());
    }
}
