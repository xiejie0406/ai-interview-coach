package com.ruoyi.fashion.infrastructure.files;

import java.text.Normalizer;
import java.util.Locale;

import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;

public final class DeliveryFileNames {
    private DeliveryFileNames() {
    }

    public static String base(DeliveryQuoteSnapshot quote) {
        return safe(quote.title(), 36) + "_" + safe(quote.quoteNo(), 32) + "_V" + quote.versionNo();
    }

    public static String safe(String value, int maximum) {
        String normalized = Normalizer.normalize(value == null ? "报价方案" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._]+|[._]+$", "");
        if (normalized.isBlank()) normalized = "报价方案";
        return normalized.substring(0, Math.min(normalized.length(), maximum));
    }

    public static String extension(String fileType) {
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "pptx" -> ".pptx";
            case "csv" -> ".csv";
            case "image_zip" -> ".zip";
            case "jpg" -> ".jpg";
            default -> throw new IllegalArgumentException("不支持的文件类型");
        };
    }
}
