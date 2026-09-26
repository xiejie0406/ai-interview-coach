package com.ruoyi.fashion.controller.rest.quote;

import java.time.LocalDate;
import java.util.List;

import com.ruoyi.fashion.application.quote.RequirementFields;

public class RequirementFieldsRequest {
    public String audience;
    public String scene;
    public String season;
    public String style;
    public List<String> preferredColors;
    public List<String> exclusions;
    public LocalDate deliveryDate;

    RequirementFields toCommand() {
        return new RequirementFields(audience, scene, season, style, preferredColors, exclusions, deliveryDate);
    }
}
