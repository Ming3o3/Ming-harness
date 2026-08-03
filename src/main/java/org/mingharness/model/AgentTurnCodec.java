package org.mingharness.model;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将 Agent 模型结果以可恢复的 JSON 形式保存到 Step，避免只留下不可重放的临时对象。 */
@Component
public class AgentTurnCodec {

    private final ObjectMapper objectMapper;

    public AgentTurnCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(ModelResponse response) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", response.content());
        value.put("reasoningContent", response.reasoningContent());
        value.put("toolCalls", response.toolCalls().stream().map(call -> Map.of(
                "id", call.id(), "name", call.name(), "arguments", call.arguments())).toList());
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法保存 Agent 模型结果", exception);
        }
    }

    public AgentTurn decode(String output) {
        if (output == null || output.isBlank()) {
            return new AgentTurn("", List.of());
        }
        try {
            JsonNode root = objectMapper.reader().readTree(output);
            if (root == null || !root.isObject() || !root.has("toolCalls")) {
                return new AgentTurn(output, List.of());
            }
            String content = root.get("content") == null ? "" : root.get("content").asText("");
            String reasoningContent = root.get("reasoningContent") == null
                    ? "" : root.get("reasoningContent").asText("");
            List<ModelToolCall> calls = new ArrayList<>();
            JsonNode rawCalls = root.get("toolCalls");
            if (rawCalls != null && rawCalls.isArray()) {
                for (JsonNode call : rawCalls) {
                    if (!call.isObject()) continue;
                    String id = call.get("id") == null ? "" : call.get("id").asText("");
                    String name = call.get("name") == null ? "" : call.get("name").asText("");
                    String arguments = call.get("arguments") == null ? "" : call.get("arguments").asText("");
                    if (!id.isBlank() && !name.isBlank() && !arguments.isBlank()) {
                        calls.add(new ModelToolCall(id, name, arguments));
                    }
                }
            }
            return new AgentTurn(content, List.copyOf(calls), reasoningContent);
        } catch (JacksonException exception) {
            // 兼容历史/自定义模型直接返回纯文本，纯文本意味着本轮没有 Tool Call。
            return new AgentTurn(output, List.of());
        }
    }

    public record AgentTurn(String content, List<ModelToolCall> toolCalls, String reasoningContent) {
        public AgentTurn(String content, List<ModelToolCall> toolCalls) {
            this(content, toolCalls, "");
        }

        public AgentTurn {
            content = content == null ? "" : content;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            reasoningContent = reasoningContent == null ? "" : reasoningContent;
        }
    }
}
