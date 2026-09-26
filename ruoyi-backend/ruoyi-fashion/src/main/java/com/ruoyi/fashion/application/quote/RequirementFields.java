package com.ruoyi.fashion.application.quote;

import java.time.LocalDate;
import java.util.List;

public record RequirementFields(
        String audience,
        String scene,
        String season,
        String style,
        List<String> preferredColors,
        List<String> exclusions,
        LocalDate deliveryDate) {
}
