package com.ruoyi.fashion.controller.rest.agent;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.fashion.application.agent.FashionAgentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FashionModuleEnabled
@RestController
@RequestMapping("/fashion/ai/agents")
public class FashionAgentController extends BaseController {
    private final FashionAgentService service;

    public FashionAgentController(FashionAgentService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:agent:list')")
    @GetMapping
    public AjaxResult list() {
        return success(service.list());
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:agent:list')")
    @GetMapping("/{agentId}/versions")
    public AjaxResult versions(@PathVariable String agentId) {
        return success(service.versions(agentId));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:agent:edit')")
    @Log(title = "智能选品 Agent", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult create(@RequestBody AgentCreateRequest request) {
        return success(service.create(request.agentCode, request.name, request.agentType,
                request.description, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:agent:edit')")
    @Log(title = "智能选品 Agent 版本", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/{agentId}/versions")
    public AjaxResult createVersion(
            @PathVariable String agentId, @RequestBody AgentVersionCreateRequest request) {
        return success(service.createVersion(agentId, request.toCommand(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('fashion:ai:agent:publish')")
    @Log(title = "智能选品 Agent 版本发布", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/{agentId}/versions/{versionId}/publish")
    public AjaxResult publish(
            @PathVariable String agentId,
            @PathVariable String versionId,
            @RequestBody AgentVersionPublishRequest request) {
        return success(service.publish(agentId, versionId, request.rowVersion, getUserId()));
    }
}
