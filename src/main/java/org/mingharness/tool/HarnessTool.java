package org.mingharness.tool;

public interface HarnessTool {

    ToolDefinition definition();

    String execute(String input);

    /** 新工具可以读取 Run/Step/租户幂等信息，旧工具继续使用字符串输入。 */
    default String execute(String input, ToolExecutionContext context) {
        return execute(input);
    }
}
