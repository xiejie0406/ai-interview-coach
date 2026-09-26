package com.ruoyi.fashion.controller.rest.quote;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.quote.pricing.FashionQuotePricingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FashionModuleEnabled
@RestController
@RequestMapping("/fashion/quotes/{quoteId}/pricing")
public class FashionQuotePricingController extends BaseController {
    private final FashionQuotePricingService service;

    public FashionQuotePricingController(FashionQuotePricingService service) { this.service = service; }

    @PreAuthorize("@ss.hasPermi('fashion:quote:query')")
    @GetMapping
    public AjaxResult get(@PathVariable String quoteId) { return success(service.get(quoteId)); }

    @PreAuthorize("@ss.hasPermi('fashion:quote:edit')")
    @Log(title = "智能选品报价核算", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping
    public AjaxResult save(@PathVariable String quoteId, @RequestBody QuotePricingRequest request) {
        return success(service.save(quoteId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:edit')")
    @Log(title = "智能选品商务例外申请", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/approval-request")
    public AjaxResult requestApproval(@PathVariable String quoteId, @RequestBody QuoteApprovalRequest request) {
        return success(service.requestApproval(quoteId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:approve')")
    @Log(title = "智能选品商务例外批准", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/approval")
    public AjaxResult approve(@PathVariable String quoteId, @RequestBody QuoteApprovalRequest request) {
        return success(service.approve(quoteId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:confirm')")
    @Log(title = "智能选品报价确认", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/confirm")
    public AjaxResult confirm(@PathVariable String quoteId, @RequestBody QuoteConfirmRequest request) {
        return success(service.confirm(quoteId, request.toCommand(), getUserId()));
    }
}
