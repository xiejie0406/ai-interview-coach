package com.ruoyi.fashion.application.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysDictTypeService;
import org.junit.jupiter.api.Test;

class FashionDictionaryGuardTest {
    @Test
    void acceptsOnlyActiveValuesFromTheSixApprovedTypes() {
        ISysDictTypeService service = mock(ISysDictTypeService.class);
        SysDictData active = entry("MAIN", "0");
        SysDictData disabled = entry("OLD", "1");
        when(service.selectDictDataByType("fashion_warehouse")).thenReturn(List.of(active, disabled));
        FashionDictionaryGuard guard = new FashionDictionaryGuard(service);

        assertDoesNotThrow(() -> guard.requireActiveValue("fashion_warehouse", "MAIN"));
        assertThrows(ServiceException.class,
                () -> guard.requireActiveValue("fashion_warehouse", "OLD"));
        assertThrows(ServiceException.class,
                () -> guard.requireActiveValue("fashion_warehouse", "MISSING"));
        assertThrows(ServiceException.class,
                () -> guard.requireActiveValue("sys_user_sex", "0"));
    }

    private static SysDictData entry(String value, String status) {
        SysDictData result = new SysDictData();
        result.setDictValue(value);
        result.setStatus(status);
        return result;
    }
}
