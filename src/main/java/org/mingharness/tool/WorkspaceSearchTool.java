package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 在工作区文本文件中搜索内容，帮助 Agent 定位代码和配置引用。 */
@Component
public class WorkspaceSearchTool implements HarnessTool {

    private final WorkspaceToolSupport support;

    public WorkspaceSearchTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public boolean available() {
        return support.properties().enabled();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.search",
                "在工作区内搜索文本，返回文件路径、行号和脱敏后的行内容；目录错误时返回 recoverable=true",
                true,
                "LOW",
                false,
                Map.of(
                        "type", "object",
                        "required", java.util.List.of("query"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "query", Map.of("type", "string", "minLength", 1, "maxLength", 200),
                                "path", Map.of("type", "string"),
                                "caseSensitive", Map.of("type", "boolean"),
                                "maxResults", Map.of("type", "integer", "minimum", 1)
                        )
                ),
                Set.of("workspace.read"),
                30_000,
                1,
                "DENY_EXTERNAL",
                Map.of("type", "object")
        );
    }

    @Override
    public String execute(String input) {
        try {
            JsonNode request = support.parseObject(input, definition().name());
            String query = support.requiredText(request, "query", definition().name());
            String rawPath = support.optionalText(request, "path");
            boolean caseSensitive = support.optionalBoolean(request, "caseSensitive", false, definition().name());
            int maxResults = support.optionalInt(request, "maxResults", support.properties().maxSearchResults(),
                    1, support.properties().maxSearchResults(), definition().name());
            Path directory = support.resolve(rawPath, false);
            java.util.List<Map<String, Object>> matches = support.search(
                    query, directory, rawPath, caseSensitive, maxResults);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("path", rawPath == null || rawPath.isBlank() ? "." : rawPath);
            result.put("matches", matches);
            result.put("truncated", matches.size() >= maxResults);
            return support.json(result);
        } catch (BusinessException exception) {
            if (!support.isRecoverableReadFailure(exception)) throw exception;
            return support.recoverableReadFailure(definition().name(), exception,
                    "请先用 workspace.list 确认搜索目录，或换一个更具体的 query");
        }
    }

    @Override
    public String execute(String input, ToolExecutionContext context) {
        return support.withWorkspace(context, () -> execute(input));
    }
}
