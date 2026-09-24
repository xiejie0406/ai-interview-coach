package com.ruoyi.fashion.application.customer;

import java.util.List;

public record CustomerPage(List<CustomerView> items, long total, int page, int pageSize) {
}
