package com.ruoyi.fashion.application.delivery;

import java.util.List;

/** 事务外生成的文件内容；成功写对象存储后只持久化摘要元数据。 */
public record GeneratedDeliveryArtifact(
        String fileName, String contentType, byte[] content, Integer pageCount,
        String role, List<String> skuCodes, String sourceMode, String reviewStatus) {
}
