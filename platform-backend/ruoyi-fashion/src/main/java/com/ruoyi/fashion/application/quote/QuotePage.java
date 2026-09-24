package com.ruoyi.fashion.application.quote;

import java.util.List;

public record QuotePage(List<QuoteView> items, long total, int page, int pageSize) {
}
