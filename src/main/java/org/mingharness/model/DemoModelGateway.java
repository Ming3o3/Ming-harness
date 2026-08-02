package org.mingharness.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "harness.model", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DemoModelGateway implements ModelGateway {

    @Override
    public ModelResponse complete(ModelRequest request) {
        String input = request.input() == null ? "" : request.input().trim();
        if (!request.tools().isEmpty()) {
            return completeAgentDemo(request, input);
        }
        String content = input.isBlank()
                ? "演示模型已收到任务，但没有可处理的输入。"
                : "演示模型分析结果：" + input;
        return response(request, input, content, List.of());
    }

    /**
     * 默认演示模型也跑一遍最小的 Agent 闭环，便于本地未配置外部模型时验证工具、步骤和审计体验。
     * 真实模型接入后仍由 OpenAI 兼容网关负责自然语言决策；演示路径只执行一次安全的只读/回显工具。
     */
    private ModelResponse completeAgentDemo(ModelRequest request, String input) {
        boolean hasToolResult = request.messages().stream()
                .anyMatch(message -> "tool".equals(message.role()));
        if (!hasToolResult) {
            Optional<ModelToolDefinition> workspaceList = request.tools().stream()
                    .filter(tool -> "workspace.list".equals(tool.name()))
                    .findFirst();
            if (workspaceList.isPresent()) {
                return response(request, input, "演示 Agent 正在检查工作区结构…",
                        List.of(new ModelToolCall("demo-workspace-list", "workspace.list",
                                "{\"path\":\".\",\"recursive\":false}")));
            }
            Optional<ModelToolDefinition> demoEcho = request.tools().stream()
                    .filter(tool -> "demo.echo".equals(tool.name()))
                    .findFirst();
            if (demoEcho.isPresent()) {
                return response(request, input, "演示 Agent 正在执行工具…",
                        List.of(new ModelToolCall("demo-echo", "demo.echo", jsonString(input))));
            }
        }

        String toolResult = request.messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .reduce((left, right) -> right)
                .map(ModelMessage::content)
                .orElse("");
        String clippedResult = toolResult.length() > 800
                ? toolResult.substring(0, 800) + "…" : toolResult;
        String content = clippedResult.isBlank()
                ? "演示 Agent 已完成任务，但没有可展示的工具结果。"
                : "演示 Agent 已完成任务。\n\n工具结果：\n" + clippedResult;
        return response(request, input, content, List.of());
    }

    private ModelResponse response(ModelRequest request, String input, String content,
                                   List<ModelToolCall> toolCalls) {
        int inputTokens = estimateTokens(input);
        int outputTokens = estimateTokens(content);
        return new ModelResponse(
                content,
                request.model() == null || request.model().isBlank() ? "demo-model" : request.model(),
                request.promptVersion() == null || request.promptVersion().isBlank() ? "prompt-v1" : request.promptVersion(),
                inputTokens,
                outputTokens,
                BigDecimal.valueOf(inputTokens + outputTokens).multiply(BigDecimal.valueOf(0.000001)),
                toolCalls
        );
    }

    private String jsonString(String value) {
        String escaped = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }

    private int estimateTokens(String value) {
        return Math.max(1, value.length() / 4);
    }
}
