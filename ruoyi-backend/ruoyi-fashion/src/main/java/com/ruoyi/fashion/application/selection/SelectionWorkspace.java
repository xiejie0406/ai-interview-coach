package com.ruoyi.fashion.application.selection;

import java.util.List;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.quote.QuoteView;

public record SelectionWorkspace(
        QuoteView quote,
        JsonNode preview,
        List<SelectionComboView> combinations) {
}
