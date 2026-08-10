package org.mingharness.education.api;

import jakarta.validation.constraints.Size;

/** 教师结课时留下的可审计说明；结课事实由服务端根据作业状态计算。 */
public record EducationCourseCompletionRequest(
        @Size(max = 2000, message = "结课说明长度不能超过 2000 个字符") String note
) {
}
