package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建学习目标；画像未指定时使用当前激活画像。 */
public record LearningGoalRequest(
        @Size(max = 128, message = "学习者画像 ID 不能超过 128 个字符") String learnerProfileId,
        @NotBlank(message = "学习目标名称不能为空")
        @Size(max = 255, message = "学习目标名称不能超过 255 个字符") String title,
        @NotBlank(message = "目标知识点不能为空")
        @Size(max = 255, message = "目标知识点不能超过 255 个字符") String conceptKey,
        @DecimalMin(value = "0.01", message = "目标掌握度必须大于 0")
        @DecimalMax(value = "1.0", message = "目标掌握度不能大于 1") Double targetMastery
) {

    public double effectiveTargetMastery() {
        return targetMastery == null ? 0.8 : targetMastery;
    }
}
