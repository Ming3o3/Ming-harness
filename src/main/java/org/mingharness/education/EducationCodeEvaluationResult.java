package org.mingharness.education;

import java.util.List;

/** 代码评测适配器返回的不可变结果；输出只作为诊断证据，不直接生成教师评分。 */
public record EducationCodeEvaluationResult(
        CodeEvaluationStatus status,
        String diagnostics,
        String output,
        Integer exitCode,
        long durationMs,
        List<EducationCodeTestCaseResult> testCaseResults
) {
    /** 兼容原有语法/编译评测调用方。 */
    public EducationCodeEvaluationResult(CodeEvaluationStatus status, String diagnostics,
                                         String output, Integer exitCode, long durationMs) {
        this(status, diagnostics, output, exitCode, durationMs, List.of());
    }

    public EducationCodeEvaluationResult {
        status = status == null ? CodeEvaluationStatus.ERROR : status;
        diagnostics = diagnostics == null ? "" : diagnostics;
        output = output == null ? "" : output;
        durationMs = Math.max(0, durationMs);
        testCaseResults = testCaseResults == null ? List.of() : List.copyOf(testCaseResults);
    }

    public static EducationCodeEvaluationResult unavailable(String message) {
        return new EducationCodeEvaluationResult(CodeEvaluationStatus.UNAVAILABLE, message, "", null, 0);
    }

    public boolean hasBehaviorEvidence() {
        return !testCaseResults.isEmpty();
    }

    public int testCaseCount() {
        return testCaseResults.size();
    }

    public int passedTestCaseCount() {
        return (int) testCaseResults.stream().filter(EducationCodeTestCaseResult::passed).count();
    }

    public double testPassRate() {
        return testCaseResults.isEmpty() ? 0.0 : (double) passedTestCaseCount() / testCaseResults.size();
    }

    public CodeBehaviorEvaluationStatus behaviorStatus() {
        if (testCaseResults.isEmpty()) return CodeBehaviorEvaluationStatus.NOT_CONFIGURED;
        if (testCaseResults.stream().allMatch(EducationCodeTestCaseResult::passed)) {
            return CodeBehaviorEvaluationStatus.PASSED;
        }
        if (testCaseResults.stream().anyMatch(item -> item.status() == CodeBehaviorEvaluationStatus.TIMEOUT)) {
            return CodeBehaviorEvaluationStatus.TIMEOUT;
        }
        if (testCaseResults.stream().anyMatch(item -> item.status() == CodeBehaviorEvaluationStatus.ERROR)) {
            return CodeBehaviorEvaluationStatus.ERROR;
        }
        return CodeBehaviorEvaluationStatus.FAILED;
    }
}
