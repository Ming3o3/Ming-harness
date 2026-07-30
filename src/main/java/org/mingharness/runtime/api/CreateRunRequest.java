package org.mingharness.runtime.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateRunRequest(
        @NotBlank(message = "租户不能为空") String tenantId,
        @NotBlank(message = "用户不能为空") String userId,
        @NotBlank(message = "任务名称不能为空") String title,
        @NotBlank(message = "任务输入不能为空") String input,
        String toolName,
        @DecimalMin(value = "0.0", inclusive = false, message = "预算必须大于 0") BigDecimal budget
) {
}
