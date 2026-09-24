package com.ruoyi.fashion.domain.shared;

/** 为 BIGINT 业务主键生成正整数；数据库不使用自增以便批量发布前预分配 ID。 */
public interface FashionIdGenerator {
    long nextId();
}
