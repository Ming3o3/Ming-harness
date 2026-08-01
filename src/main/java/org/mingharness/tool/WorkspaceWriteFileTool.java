package org.mingharness.tool;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 原子写入工作区文本文件。
 *
 * <p>这是有副作用的高风险工具：策略层要求 workspace.write 权限和人工审批，
 * 覆盖已有文件还必须携带最近一次读取得到的 SHA-256。</p>
 */
@Component
public class WorkspaceWriteFileTool implements HarnessTool {

    private final WorkspaceToolSupport support;

    public WorkspaceWriteFileTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public boolean available() {
        return support.properties().enabled();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.write",
                "原子写入工作区内 UTF-8 文本文件，覆盖文件需要 expectedSha256",
                false,
                "HIGH",
                true,
                Map.of(
                        "type", "object",
                        "required", java.util.List.of("path", "content"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "path", Map.of("type", "string", "minLength", 1),
                                "content", Map.of("type", "string"),
                                "expectedSha256", Map.of("type", "string", "minLength", 64, "maxLength", 64)
                        )
                ),
                Set.of("workspace.write"),
                30_000,
                1,
                "DENY_EXTERNAL",
                Map.of("type", "object")
        );
    }

    @Override
    public String execute(String input) {
        JsonNode request = support.parseObject(input, definition().name());
        String rawPath = support.requiredText(request, "path", definition().name());
        JsonNode contentNode = request.get("content");
        if (contentNode == null || !contentNode.isTextual()) {
            throw new org.mingharness.common.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "WORKSPACE_INPUT_INVALID",
                    "workspace.write 输入无效: 字段 content 必须是字符串");
        }
        String expectedSha256 = support.optionalText(request, "expectedSha256");
        Path path = support.resolve(rawPath, true);
        boolean created = !java.nio.file.Files.exists(path);
        String sha256 = support.writeText(path, rawPath, contentNode.asText(), expectedSha256);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", rawPath);
        result.put("bytes", contentNode.asText().getBytes(StandardCharsets.UTF_8).length);
        result.put("sha256", sha256);
        result.put("created", created);
        return support.json(result);
    }
}
