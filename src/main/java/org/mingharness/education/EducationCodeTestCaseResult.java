package org.mingharness.education;

/** 单个行为测试用例结果；期望输出只在服务端内部保留，学生投影不会直接暴露。 */
public record EducationCodeTestCaseResult(
        String caseKey,
        CodeBehaviorEvaluationStatus status,
        String actualOutput,
        String expectedOutput,
        String diagnostics,
        long durationMs
) {
    public EducationCodeTestCaseResult {
        caseKey = caseKey == null ? "" : caseKey.trim();
        status = status == null ? CodeBehaviorEvaluationStatus.ERROR : status;
        actualOutput = actualOutput == null ? "" : actualOutput;
        expectedOutput = expectedOutput == null ? "" : expectedOutput;
        diagnostics = diagnostics == null ? "" : diagnostics;
        durationMs = Math.max(0, durationMs);
    }

    public boolean passed() {
        return status == CodeBehaviorEvaluationStatus.PASSED;
    }
}
