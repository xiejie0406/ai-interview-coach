package com.ruoyi.fashion.application.product;

import java.util.List;

public record ProductPage(List<ProductView> items, long total, int page, int pageSize) {
}
