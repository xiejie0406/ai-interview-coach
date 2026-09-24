package com.ruoyi.fashion.controller.rest.quote;

import java.util.List;

import com.ruoyi.fashion.application.quote.QuoteTier;

public class QuoteTierRequest {
    public int count;
    public List<String> slots;
    public int candidateCount;

    QuoteTier toCommand() {
        return new QuoteTier(count, slots, candidateCount);
    }
}
