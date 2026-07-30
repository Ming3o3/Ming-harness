package org.mingharness.evaluation.api;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record EvaluationCaseRequest(
        @NotBlank(message = "评测用例名称不能为空") String name,
        @NotBlank(message = "评测输入不能为空") String input,
        String toolName,
        String expectedContains,
        BigDecimal budget
) {
}
