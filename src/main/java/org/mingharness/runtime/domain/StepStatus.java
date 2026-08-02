package org.mingharness.runtime.domain;

public enum StepStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    SUCCEEDED,
    /** Agent 收到人工拒绝后的可恢复工具结果，Run 会继续进入下一轮模型。 */
    REJECTED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
