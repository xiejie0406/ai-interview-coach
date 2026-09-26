package com.ruoyi.aden.domain.task;

/** 只能由服务端命令处理器应用的任务状态命令。 */
public enum AdenTaskCommand {
    SUBMIT_FOR_VALIDATION,
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    START,
    WAIT_FOR_USER,
    WAIT_FOR_EXTERNAL,
    RESUME,
    REQUEST_CANCEL,
    CONFIRM_CANCELED,
    COMPLETE,
    FAIL
}
