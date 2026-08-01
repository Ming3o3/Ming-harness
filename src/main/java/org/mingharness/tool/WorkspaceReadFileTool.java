package org.mingharness.tool;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 读取工作区内 UTF-8 文本文件，并限制返回行数，避免一次把大文件塞进上下文。 */
@Component
public class WorkspaceReadFileTool implements HarnessTool {

    private final WorkspaceToolSupport support;

    public WorkspaceReadFileTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public boolean available() {
        return support.properties().enabled();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.read",
                "读取工作区内的 UTF-8 文本文件，支持按行截取",
                true,
                "LOW",
                false,
                Map.of(
                        "type", "object",
                        "required", List.of("path"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "path", Map.of("type", "string", "minLength", 1),
                                "startLine", Map.of("type", "integer", "minimum", 1),
                                "endLine", Map.of("type", "integer", "minimum", 1)
                        )
                ),
                Set.of("workspace.read"),
                10_000,
                1,
                "DENY_EXTERNAL",
                Map.of("type", "object")
        );
    }

    @Override
    public String execute(String input) {
        JsonNode request = support.parseObject(input, definition().name());
        String rawPath = support.requiredText(request, "path", definition().name());
        int startLine = support.optionalInt(request, "startLine", 1, 1,
                support.properties().maxReadLines(), definition().name());
        int endLine = support.optionalInt(request, "endLine",
                Math.min(support.properties().maxReadLines(), startLine + support.properties().maxReadLines() - 1),
                startLine, Integer.MAX_VALUE, definition().name());
        Path path = support.resolve(rawPath, false);
        String content = support.readText(path, rawPath);
        String[] lines = content.split("\\R", -1);
        int from = Math.min(startLine - 1, lines.length);
        int to = Math.min(endLine, lines.length);
        String selected = String.join("\n", java.util.Arrays.copyOfRange(lines, from, to));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", rawPath);
        result.put("startLine", from + 1);
        result.put("endLine", to);
        result.put("totalLines", lines.length);
        result.put("content", selected);
        result.put("sha256", support.sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        result.put("truncated", from > 0 || to < lines.length);
        return support.json(result);
    }

    @Override
    public String execute(String input, ToolExecutionContext context) {
        return support.withWorkspace(context, () -> execute(input));
    }
}
