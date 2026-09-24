package com.ruoyi.fashion.application.agent.run.port;

import com.ruoyi.fashion.application.agent.run.RequirementRuntimeResult;
import com.ruoyi.fashion.application.agent.run.RunWorkItem;

public interface FashionRequirementRuntimePort {
    boolean available();

    RequirementRuntimeResult analyze(RunWorkItem workItem);

    RequirementRuntimeResult suggestProductAttributes(RunWorkItem workItem);

    RequirementRuntimeResult rankSelection(RunWorkItem workItem);
}
