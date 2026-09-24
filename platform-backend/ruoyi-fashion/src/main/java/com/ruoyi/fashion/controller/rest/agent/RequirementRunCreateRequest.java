package com.ruoyi.fashion.controller.rest.agent;

import com.ruoyi.fashion.application.agent.run.RequirementRunCommand;

public class RequirementRunCreateRequest {
    public String quoteId;
    public String sourceText;
    public String requestKey;

    RequirementRunCommand toCommand() {
        return new RequirementRunCommand(quoteId, sourceText, requestKey);
    }
}
