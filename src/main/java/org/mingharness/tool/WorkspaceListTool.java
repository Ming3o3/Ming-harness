package org.mingharness.tool;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 受工作区根目录限制的目录浏览工具，供 Agent 先了解项目结构。 */
@Component
public class WorkspaceListTool implements HarnessTool {

    private final WorkspaceToolSupport support;

    public WorkspaceListTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public boolean available() {
        return support.properties().enabled();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.list",
                "列出工作区目录结构，不跟随符号链接",
                true,
                "LOW",
                false,
                Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "path", Map.of("type", "string"),
                                "recursive", Map.of("type", "boolean")
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
        String rawPath = support.optionalText(request, "path");
        boolean recursive = support.optionalBoolean(request, "recursive", false, definition().name());
        Path directory = support.resolve(rawPath, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", rawPath == null || rawPath.isBlank() ? "." : rawPath);
        result.put("recursive", recursive);
        result.put("entries", support.list(directory, rawPath, recursive));
        return support.json(result);
    }
}
