package com.ruoyi.fashion.application.material.port;

public record StoredFashionObject(String objectKey, String sha256, long bytes, String contentType) {
}
