package org.mingharness.education.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 教师对已达标课程作业的业务确认或返工请求。 */
public record LearningAssignmentReviewRequest(
        @NotBlank(message = "审核决定不能为空")
        @Size(max = 32, message = "审核决定长度不能超过 32 个字符") String decision,
        @Size(max = 4000, message = "审核说明长度不能超过 4000 个字符") String note,
        @NotNull(message = "内容正确性评分不能为空")
        @Min(value = 1, message = "内容正确性评分必须在 1 到 5 之间")
        @Max(value = 5, message = "内容正确性评分必须在 1 到 5 之间") Integer contentCorrectnessScore,
        @NotNull(message = "证据质量评分不能为空")
        @Min(value = 1, message = "证据质量评分必须在 1 到 5 之间")
        @Max(value = 5, message = "证据质量评分必须在 1 到 5 之间") Integer evidenceQualityScore,
        @NotNull(message = "迁移准备度评分不能为空")
        @Min(value = 1, message = "迁移准备度评分必须在 1 到 5 之间")
        @Max(value = 5, message = "迁移准备度评分必须在 1 到 5 之间") Integer transferReadinessScore
) {

    /** 保留旧调用方的构造方式；审核服务会拒绝缺少量规的实际审核。 */
    public LearningAssignmentReviewRequest(String decision, String note) {
        this(decision, note, null, null, null);
    }
}
