package org.mingharness.tool;

/** 工具执行时可使用的受控上下文，不包含长期密钥或未授权业务数据。 */
public record ToolExecutionContext(
        String runId,
        String stepId,
        String tenantId,
        String idempotencyKey
) {
}
