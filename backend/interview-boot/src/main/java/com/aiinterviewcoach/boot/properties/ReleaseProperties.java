package com.aiinterviewcoach.boot.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "interview.release")
public class ReleaseProperties {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._+\\-]{0,63}")
    private String version = "dev";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
