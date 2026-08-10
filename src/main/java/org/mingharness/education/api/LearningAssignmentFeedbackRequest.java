package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 教师作业反馈请求；动作由服务层按状态和权限解释。 */
public record LearningAssignmentFeedbackRequest(
        @NotBlank(message = "反馈动作不能为空")
        @Size(max = 32, message = "反馈动作长度不能超过 32 个字符") String action,
        @NotBlank(message = "反馈内容不能为空")
        @Size(max = 4000, message = "反馈内容长度不能超过 4000 个字符") String message,
        Instant suggestedDueAt
) {
}
