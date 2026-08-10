package org.mingharness.education.api;

import jakarta.validation.constraints.Size;

/** 学习者提交作业内容；runId 为空时由服务端绑定最近一次成功教育 Run。 */
public record LearningAssignmentSubmissionRequest(
        @Size(max = 255, message = "Run ID 长度不能超过 255 个字符") String runId,
        @jakarta.validation.constraints.NotBlank(message = "作业提交内容不能为空")
        @Size(max = 8000, message = "作业提交内容长度不能超过 8000 个字符") String content
) {
}
