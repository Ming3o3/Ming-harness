package org.mingharness.tool;

public interface HarnessTool {

    ToolDefinition definition();

    String execute(String input);

    /** 新工具可以读取 Run/Step/组织幂等信息，旧工具继续使用字符串输入。 */
    default String execute(String input, ToolExecutionContext context) {
        return execute(input);
    }

    /** 有副作用工具可以提供脱敏后的执行摘要，Harness 会将其追加到同一条审计链。 */
    default ToolAudit audit(String input, String output) {
        return null;
    }

    /** 可选的运行时可用性开关；不可用工具不会被 Agent 模型契约暴露。 */
    default boolean available() {
        return true;
    }
}
