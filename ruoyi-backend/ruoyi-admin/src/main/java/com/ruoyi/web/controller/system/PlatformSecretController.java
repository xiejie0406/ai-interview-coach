package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.secret.ManagedSecretService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统管理：平台密钥。绝不提供明文读取接口。 */
@RestController
@RequestMapping("/system/platformSecret")
public class PlatformSecretController {
    private final ManagedSecretService secrets;

    public PlatformSecretController(ManagedSecretService secrets) { this.secrets = secrets; }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:list')")
    public AjaxResult list(@RequestParam(required = false) String project) {
        return AjaxResult.success(secrets.list("PLATFORM", project));
    }

    @GetMapping("/{alias}")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:list')")
    public AjaxResult metadata(@PathVariable String alias) {
        return AjaxResult.success(secrets.metadata("PLATFORM", alias));
    }

    @GetMapping("/{alias}/versions")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:list')")
    public AjaxResult versions(@PathVariable String alias) {
        return AjaxResult.success(secrets.versions("PLATFORM", alias));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:platformSecret:write')")
    @Log(title = "平台密钥轮换", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult put(@RequestBody SecretWriteRequest request) {
        return AjaxResult.success(secrets.put("PLATFORM", request.alias, request.project,
                request.provider, request.capability, request.authMode, request.name,
                request.value, request.reason, request.expectedRowVersion, operator()));
    }

    @PostMapping("/{alias}/disable")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:activate')")
    @Log(title = "平台密钥停用", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult disable(@PathVariable String alias, @RequestBody ReasonRequest request) {
        return AjaxResult.success(secrets.disable("PLATFORM", alias, request.reason,
                request.expectedRowVersion, operator()));
    }

    @PostMapping("/{alias}/stage-previous")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:write')")
    @Log(title = "平台密钥保留上一版", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult stagePrevious(@PathVariable String alias, @RequestBody ReasonRequest request) {
        return AjaxResult.success(secrets.stagePrevious(alias, request.expectedRowVersion, operator()));
    }

    @PostMapping("/{alias}/restore")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:write')")
    @Log(title = "平台密钥恢复历史版本", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult restore(@PathVariable String alias, @RequestBody RestoreRequest request) {
        return AjaxResult.success(secrets.restoreAsNewVersion("PLATFORM", alias, request.sourceVersion,
                request.expectedRowVersion, request.reason, operator()));
    }

    @GetMapping("/{alias}/audit")
    @PreAuthorize("@ss.hasPermi('system:platformSecret:audit')")
    public AjaxResult audit(@PathVariable String alias) {
        return AjaxResult.success(secrets.audit("PLATFORM", alias));
    }

    private static String operator() { return com.ruoyi.common.utils.SecurityUtils.getUsername(); }

    /** 默认 toString 禁止回显密钥。 */
    public static final class SecretWriteRequest {
        public String alias;
        public String project;
        public String provider;
        public String capability;
        public String authMode;
        public String name;
        public String value;
        public String reason;
        public Long expectedRowVersion;
        @Override public String toString() { return "SecretWriteRequest[REDACTED]"; }
    }

    public static final class ReasonRequest {
        public String reason;
        public long expectedRowVersion;
    }

    public static final class RestoreRequest {
        public int sourceVersion;
        public long expectedRowVersion;
        public String reason;
    }
}
