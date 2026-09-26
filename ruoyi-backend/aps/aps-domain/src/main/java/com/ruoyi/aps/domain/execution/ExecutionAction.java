package com.ruoyi.aps.domain.execution;

/** 影响 M25 生命周期的领域动作。报工和质量事件本身另由数量账记录。 */
public enum ExecutionAction
{
    START,
    PAUSE,
    RESUME,
    FINISH_PROCESSING,
    COMPLETE_QUALITY,
    CANCEL
}
