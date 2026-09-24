package com.ruoyi.aden.domain.runner;

public record AdenDeliveryId(String value) {
    public AdenDeliveryId { value = AdenRunnerIds.requireUuid(value, "deliveryId"); }
}
