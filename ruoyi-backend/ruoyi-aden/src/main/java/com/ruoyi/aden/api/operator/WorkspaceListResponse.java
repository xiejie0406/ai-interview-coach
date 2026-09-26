package com.ruoyi.aden.api.operator;

import java.util.List;

public record WorkspaceListResponse(List<WorkspaceSnapshot> items) {
    public WorkspaceListResponse {
        items = List.copyOf(items);
    }
}
