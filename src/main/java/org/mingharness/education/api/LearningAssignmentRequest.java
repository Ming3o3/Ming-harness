package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 教师/组织布置课程作业的请求。 */
public record LearningAssignmentRequest(
        @NotBlank(message = "学习者 ID 不能为空")
        @Size(max = 255, message = "学习者 ID 长度不能超过 255 个字符") String learnerUserId,
        @NotBlank(message = "作业标题不能为空")
        @Size(max = 255, message = "作业标题长度不能超过 255 个字符") String title,
        @NotBlank(message = "作业说明不能为空")
        @Size(max = 4000, message = "作业说明长度不能超过 4000 个字符") String instructions,
        @NotBlank(message = "学科不能为空")
        @Size(max = 128, message = "学科长度不能超过 128 个字符") String subject,
        @NotBlank(message = "年级不能为空")
        @Size(max = 128, message = "年级长度不能超过 128 个字符") String gradeLevel,
        @NotBlank(message = "课程版本不能为空")
        @Size(max = 128, message = "课程版本长度不能超过 128 个字符") String curriculumVersion,
        @NotBlank(message = "目标知识点不能为空")
        @Size(max = 255, message = "目标知识点长度不能超过 255 个字符") String conceptKey,
        @DecimalMin(value = "0.01", message = "目标掌握度必须大于 0")
        @DecimalMax(value = "1.0", message = "目标掌握度不能大于 1") Double targetMastery,
        Instant dueAt
) {
    public double effectiveTargetMastery() {
        return targetMastery == null ? 0.8 : targetMastery;
    }
}
