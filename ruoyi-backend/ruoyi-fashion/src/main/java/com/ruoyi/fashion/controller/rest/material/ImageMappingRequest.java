package com.ruoyi.fashion.controller.rest.material;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ruoyi.fashion.application.material.ImageMapping;

public class ImageMappingRequest {
    public String filename;
    public String skuCode;
    public String styleCode;
    public String colorCode;
    public String usage;
    public boolean main;
    public boolean colorConfirmed;
    public String sourceType;
    public String jdId;
    public String sourceUrl;
    public Instant capturedAt;
    public boolean allowAi;
    public boolean allowProposal;
    public boolean allowEcommerce;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("图片映射包含未知字段：" + field);
    }

    ImageMapping toCommand() {
        return new ImageMapping(filename, skuCode, styleCode, colorCode, usage, main, colorConfirmed,
                sourceType, jdId, sourceUrl, capturedAt, allowAi, allowProposal, allowEcommerce);
    }
}
