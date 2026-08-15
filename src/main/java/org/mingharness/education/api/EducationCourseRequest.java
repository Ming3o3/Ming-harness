package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 教师创建课程时提交的教学信息；课程编号可留空，由系统自动生成。 */
public record EducationCourseRequest(
        @Size(max = 128, message = "课程代码长度不能超过 128 个字符") String code,
        @NotBlank(message = "课程名称不能为空")
        @Size(max = 255, message = "课程名称长度不能超过 255 个字符") String title,
        @NotBlank(message = "学科不能为空")
        @Size(max = 128, message = "学科长度不能超过 128 个字符") String subject,
        @NotBlank(message = "年级不能为空")
        @Size(max = 128, message = "年级长度不能超过 128 个字符") String gradeLevel,
        @NotBlank(message = "课程版本不能为空")
        @Size(max = 128, message = "课程版本长度不能超过 128 个字符") String curriculumVersion
) {
}
