package com.ruoyi.fashion.controller.rest.operations;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.fashion.application.operations.FashionOperationsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fashion/operations")
public class FashionOperationsController extends BaseController {
    private final FashionOperationsService service;

    public FashionOperationsController(FashionOperationsService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:operations:query')")
    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue = "50") int limit) {
        return success(service.overview(limit));
    }

    @PreAuthorize("@ss.hasPermi('fashion:operations:retention')")
    @GetMapping("/retention/dry-run")
    public AjaxResult retentionDryRun() {
        return success(service.retentionDryRun());
    }
}
