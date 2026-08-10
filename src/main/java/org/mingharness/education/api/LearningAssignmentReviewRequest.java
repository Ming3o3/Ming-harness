package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 教师对已达标课程作业的业务确认或返工请求。 */
public record LearningAssignmentReviewRequest(
        @NotBlank(message = "审核决定不能为空")
        @Size(max = 32, message = "审核决定长度不能超过 32 个字符") String decision,
        @Size(max = 4000, message = "审核说明长度不能超过 4000 个字符") String note
) {
}
