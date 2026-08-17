package org.mingharness.education.api;

import jakarta.validation.constraints.Size;
import org.mingharness.education.LearningAssignmentSubmissionType;

/** 学习者提交作业内容；runId 为空时由服务端绑定最近一次成功教育 Run。 */
public record LearningAssignmentSubmissionRequest(
        @Size(max = 255, message = "Run ID 长度不能超过 255 个字符") String runId,
        @jakarta.validation.constraints.NotBlank(message = "作业提交内容不能为空")
        @Size(max = 100000, message = "作业提交内容长度不能超过 100000 个字符") String content,
        @Size(max = 64, message = "编程语言长度不能超过 64 个字符") String programmingLanguage,
        LearningAssignmentSubmissionType submissionType
) {
    /** 兼容原有文本提交请求。 */
    public LearningAssignmentSubmissionRequest(String runId, String content) {
        this(runId, content, null, LearningAssignmentSubmissionType.TEXT);
    }

    public LearningAssignmentSubmissionType effectiveSubmissionType() {
        return submissionType == null ? LearningAssignmentSubmissionType.TEXT : submissionType;
    }
}
