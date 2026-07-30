package org.mingharness.evaluation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EvaluationRequest(
        @NotBlank(message = "评测名称不能为空") @Size(max = 200, message = "评测名称不能超过 200 个字符") String name,
        @NotEmpty(message = "至少需要一个评测用例") @Size(max = 200, message = "单次评测最多 200 个用例")
        List<@Valid EvaluationCaseRequest> cases,
        String modelName,
        String promptVersion,
        String policyVersion
) {
}
