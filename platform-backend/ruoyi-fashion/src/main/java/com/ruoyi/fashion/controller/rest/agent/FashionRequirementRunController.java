package com.ruoyi.fashion.controller.rest.agent;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.agent.run.FashionRequirementRunService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fashion/ai/runs")
public class FashionRequirementRunController extends BaseController {
    private final FashionRequirementRunService service;

    public FashionRequirementRunController(FashionRequirementRunService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:list')")
    @GetMapping("/capabilities/requirement-analysis")
    public AjaxResult capability() {
        return success(service.capability());
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:list')")
    @GetMapping("/capabilities/product-attribute-suggestion")
    public AjaxResult productCapability() {
        return success(service.productCapability());
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:list')")
    @GetMapping("/capabilities/selection-styling")
    public AjaxResult selectionCapability() {
        return success(service.selectionCapability());
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:list')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable String id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:list')")
    @GetMapping("/by-correlation/{correlationId}")
    public AjaxResult detailByCorrelation(@PathVariable String correlationId) {
        return success(service.getByCorrelationId(correlationId));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:execute')")
    @Log(title = "智能选品需求分析", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/requirement-analysis")
    public AjaxResult create(@RequestBody RequirementRunCreateRequest request) {
        return success(service.create(request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:execute')")
    @Log(title = "智能选品商品属性建议", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/product-attribute-suggestion")
    public AjaxResult createProductAttribute(@RequestBody ProductAttributeRunCreateRequest request) {
        return success(service.createProductAttribute(request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:execute')")
    @Log(title = "智能选品搭配生成", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/selection-styling")
    public AjaxResult createSelection(@RequestBody SelectionRunCreateRequest request) {
        return success(service.createSelection(request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:cancel')")
    @Log(title = "智能选品 Agent Run 取消", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}/cancel")
    public AjaxResult cancel(@PathVariable String id, @RequestBody RunCancelRequest request) {
        return success(service.cancel(id, request.rowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:apply')")
    @Log(title = "智能选品需求分析采用", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{id}/apply-requirement")
    public AjaxResult apply(@PathVariable String id, @RequestBody RequirementApplyRequest request) {
        return success(service.applyRequirement(id, request.requestKey, request.quoteRowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:apply')")
    @Log(title = "智能选品商品属性采用", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{id}/apply-product-attributes")
    public AjaxResult applyProductAttributes(@PathVariable String id, @RequestBody ProductAttributeApplyRequest request) {
        return success(service.applyProductAttributes(
                id, request.requestKey, request.productRowVersion, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:run:apply')")
    @Log(title = "智能选品搭配采用", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{id}/apply-selection")
    public AjaxResult applySelection(@PathVariable String id, @RequestBody SelectionApplyRequest request) {
        return success(service.applySelection(id, request.requestKey, request.quoteRowVersion,
                request.comboVisualHashes, getUserId()));
    }
}
