package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 教师或学习者提交的可审计复核结果；必须附带作答、观察或评分依据。 */
public record ManualAssessmentSubmissionRequest(
        @NotBlank(message = "测评所属 Run 不能为空") @Size(max = 255) String runId,
        @NotBlank(message = "测评步骤不能为空") @Size(max = 255) String stepId,
        @NotBlank(message = "知识点不能为空") @Size(max = 255) String conceptKey,
        @NotNull(message = "测评结果不能为空") Boolean correct,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double observedMastery,
        @NotBlank(message = "测评证据不能为空") @Size(max = 4000) String evidenceText,
        @Size(max = 1000) String feedback,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Integer difficultyLevel,
        Boolean hintUsed,
        Boolean independent,
        @Size(max = 64) String questionType
) {

    /** 保留旧版七参数构造方式。 */
    public ManualAssessmentSubmissionRequest(String runId, String stepId, String conceptKey,
                                             Boolean correct, Double observedMastery,
                                             String evidenceText, String feedback) {
        this(runId, stepId, conceptKey, correct, observedMastery, evidenceText, feedback,
                null, null, null, null);
    }

    public double effectiveObservedMastery() {
        if (observedMastery != null && Double.isFinite(observedMastery)) {
            return Math.max(0.0, Math.min(1.0, observedMastery));
        }
        return Boolean.TRUE.equals(correct) ? 1.0 : 0.0;
    }

    public int effectiveDifficultyLevel() {
        return difficultyLevel == null ? 3 : Math.max(1, Math.min(5, difficultyLevel));
    }

    public boolean isHintUsed() {
        return Boolean.TRUE.equals(hintUsed);
    }

    public boolean isIndependent() {
        return independent == null || Boolean.TRUE.equals(independent);
    }
}
