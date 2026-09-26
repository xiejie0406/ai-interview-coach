package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.airole.AiRoleService;
import com.ruoyi.system.secret.ManagedSecretService;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 助手角色，与若依用户角色不是同一对象。 */
@RestController
@RequestMapping("/ai/role")
public class AiRoleController {
    private final AiRoleService roles;
    private final ManagedSecretService secrets;

    public AiRoleController(AiRoleService roles, ManagedSecretService secrets) {
        this.roles = roles;
        this.secrets = secrets;
    }

    @GetMapping("/secret-options")
    @PreAuthorize("@ss.hasPermi('ai:role:write')")
    public AjaxResult secretOptions(@RequestParam String project) {
        return AjaxResult.success(secrets.list("AI", project).stream()
                .filter(item -> "ACTIVE".equals(item.status()) && "chat".equals(item.capabilityCode())).toList());
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('ai:role:list')")
    public AjaxResult list(@RequestParam(required = false) String project) {
        return AjaxResult.success(roles.list(project));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('ai:role:list')")
    public AjaxResult get(@PathVariable long id) { return AjaxResult.success(roles.get(id)); }

    @GetMapping("/{id}/versions")
    @PreAuthorize("@ss.hasPermi('ai:role:list')")
    public AjaxResult versions(@PathVariable long id) { return AjaxResult.success(roles.versions(id)); }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('ai:role:write')")
    @Log(title = "AI 角色创建", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult create(@RequestBody CreateRole request) {
        return AjaxResult.success(roles.create(request.code, request.project, request.name,
                request.purpose, SecurityUtils.getUsername()));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("@ss.hasPermi('ai:role:write')")
    @Log(title = "AI 角色版本创建", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult createVersion(@PathVariable long id, @RequestBody CreateVersion request) {
        return AjaxResult.success(roles.createVersion(id, request.persona, request.systemPrompt,
                request.provider, request.model, request.aiSecretAlias, request.modelConfigJson,
                SecurityUtils.getUsername()));
    }

    @PostMapping("/{id}/versions/{version}/publish")
    @PreAuthorize("@ss.hasPermi('ai:role:publish')")
    @Log(title = "AI 角色发布", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult publish(@PathVariable long id, @PathVariable int version,
                              @RequestBody PublishVersion request) {
        return AjaxResult.success(roles.publish(id, version, request.expectedRowVersion,
                SecurityUtils.getUsername()));
    }

    public static final class CreateRole {
        public String code;
        public String project;
        public String name;
        public String purpose;
    }

    public static final class CreateVersion {
        public String persona;
        public String systemPrompt;
        public String provider;
        public String model;
        public String aiSecretAlias;
        public String modelConfigJson;
        @Override public String toString() { return "CreateVersion[REDACTED]"; }
    }

    public static final class PublishVersion {
        public long expectedRowVersion;
    }
}
