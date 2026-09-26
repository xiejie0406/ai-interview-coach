package com.ruoyi.aden.domain.task;

/** 领域命令来源；VALIDATOR / COORDINATOR 持久化时归入 SYSTEM actor。 */
public enum AdenTaskActor {
    OPERATOR,
    VALIDATOR,
    COORDINATOR,
    RUNNER,
    COLLECTOR
}
