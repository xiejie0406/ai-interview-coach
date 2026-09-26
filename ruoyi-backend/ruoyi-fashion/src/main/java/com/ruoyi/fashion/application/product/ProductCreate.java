package com.ruoyi.fashion.application.product;

public record ProductCreate(
        String sourceCode,
        String skuCode,
        String styleCode,
        String name,
        String categoryCode,
        String colorCode,
        String colorName,
        String sizeCode,
        String sizeSystem,
        String unit,
        String brand,
        String material,
        String season,
        String jdItemId,
        String jdUrl) {
}
