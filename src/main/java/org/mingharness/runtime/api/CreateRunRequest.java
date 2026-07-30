package org.mingharness.runtime.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRunRequest(
        @NotBlank(message = "租户不能为空") String tenantId,
        @NotBlank(message = "用户不能为空") String userId,
        @NotBlank(message = "任务名称不能为空") String title,
        @NotBlank(message = "任务输入不能为空") String input,
        String toolName,
        String modelName,
        String promptVersion,
        String policyVersion,
        @DecimalMin(value = "0.0", inclusive = false, message = "预算必须大于 0") BigDecimal budget,
        @Size(max = 128, message = "幂等键长度不能超过 128 个字符") String idempotencyKey
) {

    /** 兼容早期调用方，未传幂等键时保持原有构造方式。 */
    public CreateRunRequest(String tenantId, String userId, String title, String input,
                            String toolName, String modelName, String promptVersion,
                            String policyVersion, BigDecimal budget) {
        this(tenantId, userId, title, input, toolName, modelName, promptVersion,
                policyVersion, budget, null);
    }

    public CreateRunRequest withIdempotencyKey(String key) {
        return new CreateRunRequest(tenantId, userId, title, input, toolName, modelName,
                promptVersion, policyVersion, budget, key);
    }
}
