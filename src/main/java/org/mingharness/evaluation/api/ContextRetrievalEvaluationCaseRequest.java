package org.mingharness.evaluation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 一条检索评测用例；relevantSources 使用 document:<id> 或 memory:<id> 父来源标识。 */
public record ContextRetrievalEvaluationCaseRequest(
        @NotBlank(message = "检索评测用例名称不能为空")
        @Size(max = 200, message = "检索评测用例名称不能超过 200 个字符")
        String name,
        @NotBlank(message = "检索评测查询不能为空")
        @Size(max = 10_000, message = "检索评测查询不能超过 10000 个字符")
        String query,
        @NotEmpty(message = "每条检索评测用例至少需要一个相关来源")
        @Size(max = 50, message = "单条检索评测用例最多支持 50 个相关来源")
        List<@NotBlank @Size(max = 255) String> relevantSources,
        @Size(max = 20, message = "单条检索评测用例最多支持 20 个上下文期望片段")
        List<@NotBlank @Size(max = 500) String> expectedContains
) {

    public List<String> effectiveExpectedContains() {
        return expectedContains == null ? List.of() : expectedContains.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
