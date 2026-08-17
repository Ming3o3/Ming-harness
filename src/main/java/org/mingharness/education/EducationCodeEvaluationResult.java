package org.mingharness.education;

/** 代码评测适配器返回的不可变结果；输出只作为诊断证据，不直接生成教师评分。 */
public record EducationCodeEvaluationResult(
        CodeEvaluationStatus status,
        String diagnostics,
        String output,
        Integer exitCode,
        long durationMs
) {
    public EducationCodeEvaluationResult {
        status = status == null ? CodeEvaluationStatus.ERROR : status;
        diagnostics = diagnostics == null ? "" : diagnostics;
        output = output == null ? "" : output;
        durationMs = Math.max(0, durationMs);
    }

    public static EducationCodeEvaluationResult unavailable(String message) {
        return new EducationCodeEvaluationResult(CodeEvaluationStatus.UNAVAILABLE, message, "", null, 0);
    }
}
