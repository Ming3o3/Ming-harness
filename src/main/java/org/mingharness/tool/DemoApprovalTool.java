package org.mingharness.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DemoApprovalTool implements HarnessTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "demo.approval",
                "需要人工审批后才会执行的高风险演示工具",
                false,
                "HIGH",
                true,
                Map.of(
                        "type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", new String[]{"message"}
                )
        );
    }

    @Override
    public String execute(String input) {
        return "高风险演示操作已获批并执行: " + input;
    }
}
