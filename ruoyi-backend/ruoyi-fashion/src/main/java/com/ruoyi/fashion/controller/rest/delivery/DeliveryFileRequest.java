package com.ruoyi.fashion.controller.rest.delivery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.fashion.application.delivery.DeliveryRequestCommand;

@JsonIgnoreProperties(ignoreUnknown = false)
public record DeliveryFileRequest(String fileType, String purpose, String rendererVersion) {
    DeliveryRequestCommand toCommand() {
        return new DeliveryRequestCommand(fileType, purpose, rendererVersion);
    }
}
