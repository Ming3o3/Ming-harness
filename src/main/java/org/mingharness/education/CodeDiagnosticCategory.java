package org.mingharness.education;

/** 跨编程语言归一化的代码评测诊断类别，供学习证据统计和教学决策使用。 */
public enum CodeDiagnosticCategory {
    NONE,
    SYNTAX,
    STRUCTURE,
    IDENTIFIER,
    TYPE,
    DEPENDENCY,
    COMPILATION,
    RUNTIME,
    OUTPUT_MISMATCH,
    EDGE_CASE,
    COMPLEXITY,
    TIMEOUT,
    UNKNOWN
}
