package com.ruoyi.fashion.controller.rest.agent;

import com.ruoyi.fashion.application.agent.run.ProductAttributeRunCommand;

public class ProductAttributeRunCreateRequest {
    public String productId;
    public String requestKey;

    ProductAttributeRunCommand toCommand() {
        return new ProductAttributeRunCommand(productId, requestKey);
    }
}
