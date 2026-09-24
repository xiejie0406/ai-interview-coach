package com.ruoyi.fashion.application.importing;

public record ImportRowError(String field, String code, String valueSummary, String message) {
}
