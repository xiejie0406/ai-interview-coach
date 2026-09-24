package com.ruoyi.fashion.application.security;

import java.util.List;
import java.util.Set;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysDictTypeService;
import org.springframework.stereotype.Component;

/** 只允许六类 Fashion 平台字典中的启用值进入业务事实。 */
@Component
public final class FashionDictionaryGuard {
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "fashion_product_source", "fashion_product_category", "fashion_product_color",
            "fashion_product_unit", "fashion_product_season", "fashion_warehouse");

    private final ISysDictTypeService dictTypeService;

    public FashionDictionaryGuard(ISysDictTypeService dictTypeService) {
        this.dictTypeService = dictTypeService;
    }

    public void requireActiveValue(String dictType, String value) {
        if (!SUPPORTED_TYPES.contains(dictType)) {
            throw new ServiceException("不允许访问非 Fashion 业务字典：" + dictType);
        }
        if (value == null || value.isBlank()) {
            throw new ServiceException("字典值不能为空");
        }
        List<SysDictData> entries = dictTypeService.selectDictDataByType(dictType);
        boolean active = entries != null && entries.stream().anyMatch(entry ->
                value.equals(entry.getDictValue()) && "0".equals(entry.getStatus()));
        if (!active) {
            throw new ServiceException("字典值不存在或已停用：" + dictType + "/" + value);
        }
    }
}
