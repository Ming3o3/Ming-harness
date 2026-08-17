package org.mingharness.education;

/** 学生代码静态/编译检查的可审计状态；不等同于教师最终评分。 */
public enum CodeEvaluationStatus {
    NOT_REQUESTED,
    PASSED,
    FAILED,
    TIMEOUT,
    UNAVAILABLE,
    REJECTED,
    ERROR
}
