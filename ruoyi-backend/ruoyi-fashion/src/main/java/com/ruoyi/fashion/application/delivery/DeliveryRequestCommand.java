package com.ruoyi.fashion.application.delivery;

public record DeliveryRequestCommand(String fileType, String purpose, String rendererVersion) {
}
