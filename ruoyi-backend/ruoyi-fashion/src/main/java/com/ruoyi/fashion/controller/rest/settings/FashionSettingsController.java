package com.ruoyi.fashion.controller.rest.settings;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.settings.FashionSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fashion 设置 API；权限在服务端执行，菜单可见性不作为授权。 */
@FashionModuleEnabled
@RestController
@RequestMapping("/fashion/settings")
public class FashionSettingsController extends BaseController {
    private final FashionSettingsService settingsService;

    public FashionSettingsController(FashionSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PreAuthorize("@ss.hasPermi('fashion:settings:list')")
    @GetMapping
    public AjaxResult list() {
        return success(settingsService.list());
    }

    @PreAuthorize("@ss.hasPermi('fashion:settings:edit')")
    @Log(title = "智能选品设置", businessType = BusinessType.UPDATE, isSaveResponseData = true)
    @PutMapping
    public AjaxResult update(@Valid @RequestBody FashionSettingUpdateRequest request) {
        return success(settingsService.update(request.key(), request.value(), getUsername()));
    }
}
