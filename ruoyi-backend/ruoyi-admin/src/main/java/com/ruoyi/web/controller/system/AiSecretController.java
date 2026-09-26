package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.secret.ManagedSecretService;
import com.ruoyi.web.controller.system.PlatformSecretController.ReasonRequest;
import com.ruoyi.web.controller.system.PlatformSecretController.RestoreRequest;
import com.ruoyi.web.controller.system.PlatformSecretController.SecretWriteRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 基础设施：Provider 凭据；与平台密钥分离授权。 */
@RestController
@RequestMapping("/ai/secret")
public class AiSecretController {
    private final ManagedSecretService secrets;

    public AiSecretController(ManagedSecretService secrets) { this.secrets = secrets; }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('ai:secret:list')")
    public AjaxResult list(@RequestParam(required = false) String project) {
        return AjaxResult.success(secrets.list("AI", project));
    }

    @GetMapping("/{alias}")
    @PreAuthorize("@ss.hasPermi('ai:secret:list')")
    public AjaxResult metadata(@PathVariable String alias) {
        return AjaxResult.success(secrets.metadata("AI", alias));
    }

    @GetMapping("/{alias}/versions")
    @PreAuthorize("@ss.hasPermi('ai:secret:list')")
    public AjaxResult versions(@PathVariable String alias) {
        return AjaxResult.success(secrets.versions("AI", alias));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('ai:secret:write')")
    @Log(title = "AI 密钥轮换", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult put(@RequestBody SecretWriteRequest request) {
        return AjaxResult.success(secrets.put("AI", request.alias, request.project,
                request.provider, request.capability, request.authMode, request.name,
                request.value, request.reason, request.expectedRowVersion,
                com.ruoyi.common.utils.SecurityUtils.getUsername()));
    }

    @PostMapping("/{alias}/disable")
    @PreAuthorize("@ss.hasPermi('ai:secret:activate')")
    @Log(title = "AI 密钥停用", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult disable(@PathVariable String alias, @RequestBody ReasonRequest request) {
        return AjaxResult.success(secrets.disable("AI", alias, request.reason,
                request.expectedRowVersion,
                com.ruoyi.common.utils.SecurityUtils.getUsername()));
    }

    @PostMapping("/{alias}/restore")
    @PreAuthorize("@ss.hasPermi('ai:secret:write')")
    @Log(title = "AI 密钥恢复历史版本", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult restore(@PathVariable String alias, @RequestBody RestoreRequest request) {
        return AjaxResult.success(secrets.restoreAsNewVersion("AI", alias, request.sourceVersion,
                request.expectedRowVersion, request.reason,
                com.ruoyi.common.utils.SecurityUtils.getUsername()));
    }

    @GetMapping("/{alias}/audit")
    @PreAuthorize("@ss.hasPermi('ai:secret:audit')")
    public AjaxResult audit(@PathVariable String alias) {
        return AjaxResult.success(secrets.audit("AI", alias));
    }
}
