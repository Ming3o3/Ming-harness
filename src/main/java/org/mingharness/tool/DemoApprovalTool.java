package org.mingharness.tool;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

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
        return "高风险演示操作已获批并执行: " + input;
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
