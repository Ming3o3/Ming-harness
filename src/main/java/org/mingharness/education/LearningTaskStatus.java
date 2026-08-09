package org.mingharness.education;

/** 学习任务从产生、触达、执行到结果回写的生命周期。 */
public enum LearningTaskStatus {
    OPEN,
    IN_PROGRESS,
    AWAITING_EVIDENCE,
    DEFERRED,
    FAILED,
    COMPLETED,
    CANCELLED
}
