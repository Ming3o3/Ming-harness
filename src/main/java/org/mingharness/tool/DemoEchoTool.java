package org.mingharness.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DemoEchoTool implements HarnessTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "demo.echo",
                "返回输入内容，用于验证工具注册和执行链路",
                true,
                "LOW",
                Map.of(
                        "type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", new String[]{"message"}
                )
        );
    }

    @Override
    public String execute(String input) {
        return input == null || input.isBlank() ? "" : input;
    }
}
