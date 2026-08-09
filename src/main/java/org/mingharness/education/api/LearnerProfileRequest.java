package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LearnerProfileRequest(
        @NotBlank(message = "学科不能为空")
        @Size(max = 128, message = "学科长度不能超过 128 个字符") String subject,
        @NotBlank(message = "年级不能为空")
        @Size(max = 128, message = "年级长度不能超过 128 个字符") String gradeLevel,
        @NotBlank(message = "课程版本不能为空")
        @Size(max = 128, message = "课程版本长度不能超过 128 个字符") String curriculumVersion,
        @Size(max = 512, message = "学习目标不能超过 512 个字符") String learningGoal,
        @Size(max = 32, message = "语言标识不能超过 32 个字符") String language
) {
}
