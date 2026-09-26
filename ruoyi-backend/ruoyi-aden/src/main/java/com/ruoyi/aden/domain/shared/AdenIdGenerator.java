package com.ruoyi.aden.domain.shared;

/** 领域对象所需的不透明标识生成端口。 */
@FunctionalInterface
public interface AdenIdGenerator {
    String nextId();
}
