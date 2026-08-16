package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MasteryUpdateRequest(
        @NotBlank(message = "知识点不能为空")
        @Size(max = 255, message = "知识点长度不能超过 255 个字符") String conceptKey,
        @DecimalMin(value = "0.0", message = "掌握度不能小于 0")
        @DecimalMax(value = "1.0", message = "掌握度不能大于 1") Double masteryScore,
        Boolean correct,
        @Min(value = 0, message = "答题次数不能小于 0") Integer attempts,
        @Min(value = 0, message = "答对次数不能小于 0") Integer correctAttempts,
        @Min(value = 1, message = "题目难度必须在 1 到 5 之间")
        @Max(value = 5, message = "题目难度必须在 1 到 5 之间") Integer difficultyLevel,
        @DecimalMin(value = "0.0", message = "证据权重不能小于 0")
        @DecimalMax(value = "1.0", message = "证据权重不能大于 1") Double evidenceWeight,
        Boolean hintUsed,
        Boolean independentEvidence
) {

    /** 保留旧版五参数构造方式，避免已有接口和测试中断。 */
    public MasteryUpdateRequest(String conceptKey, Double masteryScore, Boolean correct,
                                Integer attempts, Integer correctAttempts) {
        this(conceptKey, masteryScore, correct, attempts, correctAttempts, null, null, null, null);
    }

    public double effectiveMasteryScore() {
        return masteryScore == null ? (Boolean.TRUE.equals(correct) ? 1.0 : 0.0) : masteryScore;
    }

    public boolean isObservation() {
        return correct != null;
    }

    public int effectiveDifficultyLevel() {
        return difficultyLevel == null ? 3 : Math.max(1, Math.min(5, difficultyLevel));
    }

    public double effectiveEvidenceWeight() {
        if (evidenceWeight == null || !Double.isFinite(evidenceWeight)) return 1.0;
        return Math.max(0.25, Math.min(1.0, evidenceWeight));
    }

    public boolean isHintUsed() {
        return Boolean.TRUE.equals(hintUsed);
    }

    public boolean isIndependentEvidence() {
        return independentEvidence == null || Boolean.TRUE.equals(independentEvidence);
    }
}
