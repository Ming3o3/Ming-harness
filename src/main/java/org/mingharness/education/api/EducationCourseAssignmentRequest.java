package org.mingharness.education.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 教师向整个课程活跃名单布置统一知识目标；课程约束从课程实例读取。 */
public record EducationCourseAssignmentRequest(
        @NotBlank(message = "作业标题不能为空")
        @Size(max = 255, message = "作业标题长度不能超过 255 个字符") String title,
        @NotBlank(message = "作业说明不能为空")
        @Size(max = 4000, message = "作业说明长度不能超过 4000 个字符") String instructions,
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
