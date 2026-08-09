package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MasteryUpdateRequest(
        @NotBlank(message = "知识点不能为空")
        @Size(max = 255, message = "知识点长度不能超过 255 个字符") String conceptKey,
        @DecimalMin(value = "0.0", message = "掌握度不能小于 0")
        @DecimalMax(value = "1.0", message = "掌握度不能大于 1") Double masteryScore,
        Boolean correct,
        @Min(value = 0, message = "答题次数不能小于 0") Integer attempts,
        @Min(value = 0, message = "答对次数不能小于 0") Integer correctAttempts
) {

    public double effectiveMasteryScore() {
        return masteryScore == null ? (Boolean.TRUE.equals(correct) ? 1.0 : 0.0) : masteryScore;
    }

    public boolean isObservation() {
        return correct != null;
    }
}
