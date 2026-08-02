package org.mingharness.tool;

import org.mingharness.common.BusinessException;
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
                "列出工作区目录结构，不跟随符号链接；路径错误会返回 recoverable=true，Agent 可重新浏览根目录",
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
        try {
            JsonNode request = support.parseObject(input, definition().name());
            String rawPath = support.optionalText(request, "path");
            boolean recursive = support.optionalBoolean(request, "recursive", false, definition().name());
            Path directory = support.resolve(rawPath, false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", rawPath == null || rawPath.isBlank() ? "." : rawPath);
            result.put("recursive", recursive);
            result.put("entries", support.list(directory, rawPath, recursive));
            return support.json(result);
        } catch (BusinessException exception) {
            if (!support.isRecoverableReadFailure(exception)) throw exception;
            return support.recoverableReadFailure(definition().name(), exception,
                    "请改用 workspace.list 的 path=. 重新浏览工作区，再选择存在的目录");
        }
    }

    @Override
    public String execute(String input, ToolExecutionContext context) {
        return support.withWorkspace(context, () -> execute(input));
    }
}
