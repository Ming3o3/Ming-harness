package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 教师向课程名单中添加学习者。 */
public record EducationEnrollmentRequest(
        @NotBlank(message = "学习者 ID 不能为空")
        @Size(max = 255, message = "学习者 ID 长度不能超过 255 个字符") String learnerUserId
) {
}
