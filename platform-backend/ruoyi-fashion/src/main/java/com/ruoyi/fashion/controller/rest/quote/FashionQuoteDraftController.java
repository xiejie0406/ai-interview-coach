package com.ruoyi.fashion.controller.rest.quote;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.quote.FashionQuoteDraftService;
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
@RequestMapping("/fashion/quotes")
public class FashionQuoteDraftController extends BaseController {
    private final FashionQuoteDraftService service;

    public FashionQuoteDraftController(FashionQuoteDraftService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:list')")
    @GetMapping
    public AjaxResult list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(service.search(status, keyword, page, pageSize));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable String id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:add')")
    @Log(title = "智能选品客户方案", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult create(@RequestBody QuoteDraftRequest request) {
        return success(service.create(request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:edit')")
    @Log(title = "智能选品方案草稿", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable String id, @RequestBody QuoteDraftRequest request) {
        return success(service.update(id, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:edit')")
    @Log(title = "智能选品需求确认", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}/requirements/confirm")
    public AjaxResult confirmRequirements(@PathVariable String id, @RequestBody RequirementConfirmRequest request) {
        if (request.requirements == null) {
            return error("确认需求不能为空");
        }
        return success(service.confirmRequirements(id, request.requirements.toCommand(), request.rowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:add')")
    @Log(title = "智能选品方案复制", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/{id}/copy")
    public AjaxResult copy(@PathVariable String id) {
        return success(service.copyRevision(id, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:quote:edit')")
    @Log(title = "智能选品方案关闭", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}/close")
    public AjaxResult close(@PathVariable String id, @RequestBody QuoteVersionRequest request) {
        return success(service.close(id, request.rowVersion, getUserId()));
    }
}
