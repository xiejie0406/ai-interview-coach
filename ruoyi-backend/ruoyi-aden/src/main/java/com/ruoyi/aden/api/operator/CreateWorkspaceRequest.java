package com.ruoyi.aden.api.operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^[^\\r\\n]+$")
        String displayName) {
}
