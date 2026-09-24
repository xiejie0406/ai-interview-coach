package com.ruoyi.fashion.controller.rest.customer;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.customer.FashionCustomerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fashion/customers")
public class FashionCustomerController extends BaseController {
    private final FashionCustomerService service;

    public FashionCustomerController(FashionCustomerService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:customer:list')")
    @GetMapping
    public AjaxResult list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(service.search(status, keyword, page, pageSize));
    }

    @PreAuthorize("@ss.hasPermi('fashion:customer:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable String id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('fashion:customer:add')")
    @Log(title = "智能选品客户", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult create(@RequestBody CustomerCreateRequest request) {
        return success(service.create(request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:customer:edit')")
    @Log(title = "智能选品客户", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable String id, @RequestBody CustomerUpdateRequest request) {
        return success(service.update(id, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:customer:edit')")
    @Log(title = "智能选品客户归档", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}/archive")
    public AjaxResult archive(@PathVariable String id, @RequestBody CustomerStatusRequest request) {
        return success(service.archive(id, request.rowVersion, getUserId()));
    }
}
