package org.mingharness.education.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 请求服务端执行学习目标当前推荐动作；空会话 ID 会自动创建学习会话。 */
public record ExecuteLearningActionRequest(
        @Size(max = 128, message = "会话 ID 长度不能超过 128 个字符") String conversationId,
        @Size(max = 128, message = "模型名称长度不能超过 128 个字符") String modelName,
        @Min(value = 1, message = "Agent 最大轮数必须至少为 1")
        @Max(value = 1000, message = "Agent 最大轮数不能超过 1000") Integer maxTurns,
        @Size(max = 128, message = "课程作业 ID 长度不能超过 128 个字符") String learningAssignmentId,
        @Size(max = 128, message = "课程实例 ID 长度不能超过 128 个字符") String courseId
) {

    /** 兼容只指定会话、模型和最大轮数的旧调用方。 */
    public ExecuteLearningActionRequest(String conversationId, String modelName, Integer maxTurns) {
        this(conversationId, modelName, maxTurns, null, null);
    }

    /** 兼容增加课程实例绑定前的作业启动请求。 */
    public ExecuteLearningActionRequest(String conversationId, String modelName, Integer maxTurns,
                                        String learningAssignmentId) {
        this(conversationId, modelName, maxTurns, learningAssignmentId, null);
    }

    public int effectiveMaxTurns() {
        return maxTurns == null ? 1_000 : Math.max(1, Math.min(1_000, maxTurns));
    }
}
