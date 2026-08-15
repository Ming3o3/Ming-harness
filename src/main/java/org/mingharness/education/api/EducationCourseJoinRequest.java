package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学习者使用教师分享的邀请码加入课程；账号由当前认证身份决定。 */
public record EducationCourseJoinRequest(
        @NotBlank(message = "课程邀请码不能为空")
        @Size(max = 12, message = "课程邀请码长度不能超过 12 个字符") String joinCode
) {
}
