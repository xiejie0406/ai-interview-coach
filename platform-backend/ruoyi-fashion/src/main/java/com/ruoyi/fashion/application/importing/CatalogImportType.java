package com.ruoyi.fashion.application.importing;

import java.util.Map;
import java.util.Set;

import com.ruoyi.common.exception.ServiceException;

public enum CatalogImportType {
    PRICE("price", "fashion-price", Map.of(
            "sourceCode", "来源编码", "skuCode", "SKU编码", "salePrice", "销售单价",
            "currency", "币种", "taxMode", "含税口径", "asOf", "业务时间")),
    STOCK("stock", "fashion-stock", Map.of(
            "sourceCode", "来源编码", "skuCode", "SKU编码", "warehouseCode", "仓库编码",
            "availableQty", "可售数量", "asOf", "业务时间"));

    private final String code;
    private final String templateCode;
    private final Map<String, String> headers;

    CatalogImportType(String code, String templateCode, Map<String, String> headers) {
        this.code = code;
        this.templateCode = templateCode;
        this.headers = headers;
    }

    public String code() {
        return code;
    }

    public String templateCode() {
        return templateCode;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public Set<String> fields() {
        return headers.keySet();
    }

    public static CatalogImportType fromCode(String value) {
        for (CatalogImportType type : values()) {
            if (type.code.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new ServiceException("导入类型只允许 price 或 stock");
    }
}
