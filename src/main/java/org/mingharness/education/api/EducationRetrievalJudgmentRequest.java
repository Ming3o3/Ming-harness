package org.mingharness.education.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 教师对单条 Run 证据的教育效用量规。 */
public record EducationRetrievalJudgmentRequest(
        String stepId,
        @NotBlank(message = "证据引用不能为空")
        @Size(max = 1500, message = "证据引用长度不能超过 1500 个字符") String evidenceCitation,
        @NotNull(message = "目标 grounding 评分不能为空")
        @Min(value = 1, message = "目标 grounding 评分必须在 1 到 5 之间")
        @Max(value = 5, message = "目标 grounding 评分必须在 1 到 5 之间") Integer targetGroundingScore,
        @NotNull(message = "前置补强评分不能为空")
        @Min(value = 1, message = "前置补强评分必须在 1 到 5 之间")
        @Max(value = 5, message = "前置补强评分必须在 1 到 5 之间") Integer prerequisiteUtilityScore,
        @NotNull(message = "难度适配评分不能为空")
        @Min(value = 1, message = "难度适配评分必须在 1 到 5 之间")
        @Max(value = 5, message = "难度适配评分必须在 1 到 5 之间") Integer difficultyFitScore,
        @NotNull(message = "总体效用评分不能为空")
        @Min(value = 1, message = "总体效用评分必须在 1 到 5 之间")
        @Max(value = 5, message = "总体效用评分必须在 1 到 5 之间") Integer overallUtilityScore,
        @Size(max = 4000, message = "标注说明长度不能超过 4000 个字符") String note
) {
}
