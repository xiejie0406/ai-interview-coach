package com.ruoyi.aden.contract;

/**
 * Operator API 当前允许反序列化的公开任务命令。
 *
 * <p>内部 Validator、Coordinator 和 Runner receipt adapter 命令必须使用领域内部类型，
 * 不得加入此枚举。</p>
 */
public enum OperatorTaskCommand {
    SUBMIT_FOR_VALIDATION,
    REQUEST_CANCEL;

    /** 精确匹配 wire 值并对未知值 fail closed。 */
    public static OperatorTaskCommand parseWireValue(Object wireValue) {
        if (!(wireValue instanceof String command)) {
            throw new IllegalArgumentException("OperatorTaskCommand 必须是 JSON string");
        }
        try {
            return valueOf(command);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知或非公开的 OperatorTaskCommand", exception);
        }
    }
}
