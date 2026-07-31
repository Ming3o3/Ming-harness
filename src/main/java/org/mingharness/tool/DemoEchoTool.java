package org.mingharness.tool;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class DemoEchoTool implements HarnessTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "demo.echo",
                "返回输入内容，用于验证工具注册和执行链路",
                true,
                "LOW",
                false,
                legacyTextSchema(),
                Set.of(),
                30_000,
                1,
                "DENY_EXTERNAL",
                legacyTextSchema()
        );
    }

    @Override
    public String execute(String input) {
        return input == null || input.isBlank() ? "" : input;
    }

    /** 演示工具继续接收旧版纯文本，同时明确声明其实际类型为字符串。 */
    private Map<String, Object> legacyTextSchema() {
        return Map.of(
                "type", "string",
                "minLength", 1,
                "x-harness-legacy-text", true
        );
    }
}
