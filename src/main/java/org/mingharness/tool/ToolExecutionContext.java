package org.mingharness.tool;

/** 工具执行时可使用的受控上下文，不包含长期密钥或未授权业务数据。 */
public record ToolExecutionContext(
        String runId,
        String stepId,
        String tenantId,
        String userId,
        String workspaceId,
        String idempotencyKey
) {

    /** 兼容工作区多选之前的工具实现，未绑定时仍使用服务端配置的默认工作区。 */
    public ToolExecutionContext(String runId, String stepId, String tenantId, String idempotencyKey) {
        this(runId, stepId, tenantId, null, null, idempotencyKey);
    }
}
