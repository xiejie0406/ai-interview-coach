package com.ruoyi.interview.infrastructure.persistence.platform;

import com.ruoyi.interview.application.platform.port.DurableStreamPort;
import com.ruoyi.interview.domain.platform.DomainEvent;

import java.util.Optional;

/**
 * 领域事件到公开 durable stream 的显式 allowlist。返回 empty 只能表示该事件已被明确判定为
 * 非 durable；Interview/Evaluation 命名空间出现未注册事件时实现必须 fail closed。
 */
public interface DurableStreamEventPolicy {

    Optional<DurableStreamPort.AppendCommand> classify(DomainEvent event);
}

