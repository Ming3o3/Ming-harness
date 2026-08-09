package org.mingharness.evaluation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.math.BigDecimal;

public record EvaluationRequest(
        @NotBlank(message = "评测名称不能为空") @Size(max = 200, message = "评测名称不能超过 200 个字符") String name,
        @NotEmpty(message = "至少需要一个评测用例") @Size(max = 200, message = "单次评测最多 200 个用例")
        List<@Valid EvaluationCaseRequest> cases,
        String modelName,
        String promptVersion,
        String policyVersion,
        @Size(max = 128, message = "基线报告 ID 长度不能超过 128 个字符") String baselineReportId,
        @jakarta.validation.constraints.DecimalMin(value = "0.0", inclusive = true, message = "最低通过率不能小于 0")
        @jakarta.validation.constraints.DecimalMax(value = "1.0", message = "最低通过率不能大于 1")
        BigDecimal minimumSuccessRate
) {

    public EvaluationRequest(String name, List<@Valid EvaluationCaseRequest> cases,
                              String modelName, String promptVersion, String policyVersion) {
        this(name, cases, modelName, promptVersion, policyVersion, null, null);
    }
}
