package com.ruoyi.fashion.controller.rest.delivery;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RetentionExtensionRequest(long rowVersion, Instant retainUntil) {
}
