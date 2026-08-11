package org.mingharness.education.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 第二评分者提交的独立量规评价，不改变作业状态。 */
public record LearningAssignmentEvaluationRequest(
        @NotNull(message = "内容正确性评分不能为空")
        @Min(value = 1, message = "内容正确性评分必须在 1 到 5 之间")
        @Max(value = 5, message = "内容正确性评分必须在 1 到 5 之间") Integer contentCorrectnessScore,
        @NotNull(message = "证据质量评分不能为空")
        @Min(value = 1, message = "证据质量评分必须在 1 到 5 之间")
        @Max(value = 5, message = "证据质量评分必须在 1 到 5 之间") Integer evidenceQualityScore,
        @NotNull(message = "迁移准备度评分不能为空")
        @Min(value = 1, message = "迁移准备度评分必须在 1 到 5 之间")
        @Max(value = 5, message = "迁移准备度评分必须在 1 到 5 之间") Integer transferReadinessScore,
        @Size(max = 4000, message = "评价说明长度不能超过 4000 个字符") String note
) {
}
