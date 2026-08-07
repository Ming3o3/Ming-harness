package org.mingharness.evaluation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 有界的上下文检索离线评测请求。 */
public record ContextRetrievalEvaluationRequest(
        @NotBlank(message = "检索评测名称不能为空")
        @Size(max = 200, message = "检索评测名称不能超过 200 个字符")
        String name,
        @NotEmpty(message = "至少需要一个检索评测用例")
        @Size(max = 200, message = "单次检索评测最多 200 个用例")
        List<@Valid ContextRetrievalEvaluationCaseRequest> cases,
        @Min(value = 1, message = "topK 至少为 1")
        @Max(value = 50, message = "topK 不能超过 50")
        Integer topK,
        @Min(value = 1, message = "上下文字符上限至少为 1")
        @Max(value = 20_000, message = "上下文字符上限不能超过 20000")
        Integer maxChars
) {

    public int effectiveTopK() {
        return topK == null ? 5 : Math.min(50, Math.max(1, topK));
    }

    public int effectiveMaxChars() {
        return maxChars == null ? 4_000 : Math.min(20_000, Math.max(1, maxChars));
    }
}
