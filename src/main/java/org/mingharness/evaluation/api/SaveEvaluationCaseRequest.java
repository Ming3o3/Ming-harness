package org.mingharness.evaluation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveEvaluationCaseRequest(
        @NotBlank(message = "Run ID 不能为空") @Size(max = 128, message = "Run ID 长度不能超过 128 个字符") String runId,
        @Size(max = 200, message = "评测用例名称不能超过 200 个字符") String name,
        @Size(max = 2000, message = "期望包含内容不能超过 2000 个字符") String expectedContains
) {
}
