package com.ruoyi.fashion.application.importing;

import java.util.List;
import java.util.Map;

public record ParsedCatalogValueRow(
        int sourceRowNo,
        Map<String, String> values,
        List<ImportRowError> errors) {
}
