package org.mingharness.education.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 将到期学习任务延期一段可解释的天数。 */
public record DeferLearningTaskRequest(
        @Min(value = 1, message = "延期天数至少为 1")
        @Max(value = 30, message = "延期天数不能超过 30") Integer days
) {

    public int effectiveDays() {
        return days == null ? 1 : Math.max(1, Math.min(30, days));
    }
}
