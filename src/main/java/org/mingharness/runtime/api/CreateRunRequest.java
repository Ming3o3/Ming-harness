package org.mingharness.runtime.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.mingharness.runtime.domain.RunScenario;

import java.math.BigDecimal;

public record CreateRunRequest(
        @NotBlank(message = "组织不能为空") String tenantId,
        @NotBlank(message = "用户不能为空") String userId,
        @NotBlank(message = "任务名称不能为空") String title,
        @NotBlank(message = "任务输入不能为空") String input,
        String toolName,
        String modelName,
        String promptVersion,
        String policyVersion,
        @DecimalMin(value = "0.0", inclusive = false, message = "预算必须大于 0") BigDecimal budget,
        @Size(max = 128, message = "幂等键长度不能超过 128 个字符") String idempotencyKey,
        @Size(max = 1000, message = "权限快照长度不能超过 1000 个字符") String permissions,
        Boolean agentMode,
        @Min(value = 1, message = "Agent 最大轮数必须至少为 1")
        @Max(value = 1000, message = "Agent 最大轮数不能超过 1000") Integer maxTurns,
        @Size(max = 128, message = "会话 ID 长度不能超过 128 个字符") String conversationId,
        @Size(max = 128, message = "工作区 ID 长度不能超过 128 个字符") String workspaceId,
        RunScenario scenario
) {

    /** 兼容早期调用方，未传幂等键时保持原有构造方式。 */
    public CreateRunRequest(String tenantId, String userId, String title, String input,
                            String toolName, String modelName, String promptVersion,
                            String policyVersion, BigDecimal budget) {
        this(tenantId, userId, title, input, toolName, modelName, promptVersion,
                policyVersion, budget, null, null, false, null, null, null, null);
    }

    /** 兼容只增加幂等键的调用方，权限快照默认为空。 */
    public CreateRunRequest(String tenantId, String userId, String title, String input,
                            String toolName, String modelName, String promptVersion,
                            String policyVersion, BigDecimal budget, String idempotencyKey) {
        this(tenantId, userId, title, input, toolName, modelName, promptVersion,
                policyVersion, budget, idempotencyKey, null, false, null, null, null, null);
    }

    /** 兼容已经携带权限快照的旧调用方。 */
    public CreateRunRequest(String tenantId, String userId, String title, String input,
                            String toolName, String modelName, String promptVersion,
                            String policyVersion, BigDecimal budget, String idempotencyKey,
                            String permissions) {
        this(tenantId, userId, title, input, toolName, modelName, promptVersion,
                policyVersion, budget, idempotencyKey, permissions, false, null, null, null, null);
    }

    /** 兼容已经携带 Agent 配置的调用方，会话关联默认为空。 */
    public CreateRunRequest(String tenantId, String userId, String title, String input,
                            String toolName, String modelName, String promptVersion,
                            String policyVersion, BigDecimal budget, String idempotencyKey,
                            String permissions, Boolean agentMode, Integer maxTurns) {
        this(tenantId, userId, title, input, toolName, modelName, promptVersion,
                policyVersion, budget, idempotencyKey, permissions, agentMode, maxTurns, null, null, null);
    }

    /** 兼容上一版已经携带会话和工作区字段的调用方。 */
    public CreateRunRequest(String tenantId, String userId, String title, String input,
                            String toolName, String modelName, String promptVersion,
                            String policyVersion, BigDecimal budget, String idempotencyKey,
                            String permissions, Boolean agentMode, Integer maxTurns,
                            String conversationId, String workspaceId) {
        this(tenantId, userId, title, input, toolName, modelName, promptVersion,
                policyVersion, budget, idempotencyKey, permissions, agentMode, maxTurns,
                conversationId, workspaceId, null);
    }

    public CreateRunRequest withIdempotencyKey(String key) {
        return new CreateRunRequest(tenantId, userId, title, input, toolName, modelName,
                promptVersion, policyVersion, budget, key, permissions, agentMode, maxTurns,
                conversationId, workspaceId, scenario);
    }

    /** 由边缘认证层注入当前用户权限快照。 */
    public CreateRunRequest withPermissions(String value) {
        return new CreateRunRequest(tenantId, userId, title, input, toolName, modelName,
                promptVersion, policyVersion, budget, idempotencyKey, value, agentMode, maxTurns,
                conversationId, workspaceId, scenario);
    }

    /** 将当前 Run 绑定到聊天会话，旧的独立 Run 保持 null。 */
    public CreateRunRequest withConversationId(String value) {
        return new CreateRunRequest(tenantId, userId, title, input, toolName, modelName,
                promptVersion, policyVersion, budget, idempotencyKey, permissions,
                agentMode, maxTurns, value, workspaceId, scenario);
    }

    /** 将独立 Run 或聊天 Run 固定到用户已授权的本地工作区。 */
    public CreateRunRequest withWorkspaceId(String value) {
        return new CreateRunRequest(tenantId, userId, title, input, toolName, modelName,
                promptVersion, policyVersion, budget, idempotencyKey, permissions,
                agentMode, maxTurns, conversationId, value, scenario);
    }

    public CreateRunRequest withScenario(RunScenario value) {
        return new CreateRunRequest(tenantId, userId, title, input, toolName, modelName,
                promptVersion, policyVersion, budget, idempotencyKey, permissions,
                agentMode, maxTurns, conversationId, workspaceId, value);
    }

    public boolean isAgentMode() {
        return Boolean.TRUE.equals(agentMode);
    }

    public int effectiveMaxTurns() {
        return maxTurns == null ? 1_000 : Math.max(1, Math.min(1_000, maxTurns));
    }
}
