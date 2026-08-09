package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LearningGoalStatusRequest(
        @NotBlank(message = "学习目标状态不能为空")
        @Size(max = 32, message = "学习目标状态长度不能超过 32 个字符") String status
) {
}
