package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对工作区文本文件执行精确替换编辑。
 *
 * <p>Agent 先通过 workspace.read 获取文件内容和 SHA-256，再提交一个或多个精确文本替换。
 * 每次编辑都会重新校验文件哈希并通过工作区支持类原子写入，避免覆盖并发修改或半写入文件。</p>
 */
@Component
public class WorkspaceEditFileTool implements HarnessTool {

    private static final int MAX_EDITS = 32;
    private static final int MAX_REPLACEMENTS_PER_EDIT = 256;

    private final WorkspaceToolSupport support;

    public WorkspaceEditFileTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.edit",
                "按精确文本片段增量编辑工作区内 UTF-8 文件，避免整文件覆盖",
                false,
                "HIGH",
                true,
                Map.of(
                        "type", "object",
                        "required", List.of("path", "edits", "expectedSha256"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "path", Map.of("type", "string", "minLength", 1),
                                "expectedSha256", Map.of("type", "string", "minLength", 64, "maxLength", 64),
                                "edits", Map.of(
                                        "type", "array",
                                        "minItems", 1,
                                        "maxItems", MAX_EDITS,
                                        "items", Map.of(
                                                "type", "object",
                                                "required", List.of("oldText", "newText"),
                                                "additionalProperties", false,
                                                "properties", Map.of(
                                                        "oldText", Map.of("type", "string", "minLength", 1),
                                                        "newText", Map.of("type", "string"),
                                                        "replaceAll", Map.of("type", "boolean")
                                                )
                                        )
                                )
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
    public boolean available() {
        return support.properties().enabled();
    }

    @Override
    public String execute(String input) {
        JsonNode request = support.parseObject(input, definition().name());
        String rawPath = support.requiredText(request, "path", definition().name());
        String expectedSha256 = support.requiredText(request, "expectedSha256", definition().name());
        JsonNode rawEdits = request.get("edits");
        if (rawEdits == null || !rawEdits.isArray() || rawEdits.isEmpty()
                || rawEdits.size() > MAX_EDITS) {
            throw invalidInput("edits 必须包含 1 到 " + MAX_EDITS + " 个编辑");
        }

        Path path = support.resolve(rawPath, false);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "WORKSPACE_EDIT_TARGET_NOT_FOUND",
                    "编辑目标不存在: " + rawPath);
        }
        String original = support.readText(path, rawPath);
        String actualSha256 = support.sha256(original.getBytes(StandardCharsets.UTF_8));
        if (!actualSha256.equalsIgnoreCase(expectedSha256.trim())) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_FILE_CHANGED",
                    "文件在读取后已发生变化，请重新读取后再编辑");
        }

        List<EditRequest> edits = parseEdits(rawEdits);
        String updated = original;
        int replacementCount = 0;
        for (int index = 0; index < edits.size(); index++) {
            EditRequest edit = edits.get(index);
            int occurrences = countOccurrences(updated, edit.oldText());
            if (occurrences == 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_EDIT_NOT_FOUND",
                        "第 " + (index + 1) + " 个编辑找不到 oldText");
            }
            if (!edit.replaceAll() && occurrences != 1) {
                throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_EDIT_AMBIGUOUS",
                        "第 " + (index + 1) + " 个编辑匹配到 " + occurrences
                                + " 处，请提供更精确的 oldText 或显式设置 replaceAll");
            }
            if (occurrences > MAX_REPLACEMENTS_PER_EDIT) {
                throw invalidInput("第 " + (index + 1) + " 个编辑的匹配数量超过限制");
            }
            updated = replace(updated, edit.oldText(), edit.newText(), edit.replaceAll());
            replacementCount += edit.replaceAll() ? occurrences : 1;
        }

        if (updated.equals(original)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_EDIT_NO_CHANGE",
                    "编辑没有产生文件变化");
        }
        String sha256 = support.writeText(path, rawPath, updated, expectedSha256);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", rawPath);
        result.put("edits", edits.size());
        result.put("replacements", replacementCount);
        result.put("bytes", updated.getBytes(StandardCharsets.UTF_8).length);
        result.put("sha256", sha256);
        result.put("changed", true);
        return support.json(result);
    }

    @Override
    public ToolAudit audit(String input, String output) {
        JsonNode result = support.parseObject(output, definition().name());
        String path = support.optionalText(result, "path");
        int edits = result.path("edits").asInt(0);
        int replacements = result.path("replacements").asInt(0);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", path);
        metadata.put("edits", edits);
        metadata.put("replacements", replacements);
        metadata.put("bytes", result.path("bytes").asInt(0));
        metadata.put("sha256", result.path("sha256").asText(""));
        return new ToolAudit("WORKSPACE_FILE_EDITED",
                "工作区文件编辑: " + (path == null ? "未知文件" : path)
                        + "，应用 " + edits + " 个编辑，替换 " + replacements + " 处",
                support.json(metadata));
    }

    private List<EditRequest> parseEdits(JsonNode rawEdits) {
        List<EditRequest> edits = new ArrayList<>(rawEdits.size());
        long inputBytes = 0;
        for (JsonNode item : rawEdits) {
            if (!item.isObject()) throw invalidInput("edits 中每一项必须是对象");
            JsonNode oldText = item.get("oldText");
            JsonNode newText = item.get("newText");
            if (oldText == null || !oldText.isTextual() || oldText.asText().isEmpty()
                    || newText == null || !newText.isTextual()) {
                throw invalidInput("每个编辑必须包含非空 oldText 和字符串 newText");
            }
            JsonNode replaceAll = item.get("replaceAll");
            if (replaceAll != null && !replaceAll.isBoolean()) {
                throw invalidInput("replaceAll 必须是布尔值");
            }
            inputBytes += oldText.asText().getBytes(StandardCharsets.UTF_8).length;
            inputBytes += newText.asText().getBytes(StandardCharsets.UTF_8).length;
            if (inputBytes > Math.max(support.properties().maxReadBytes(),
                    support.properties().maxWriteBytes()) * 2L) {
                throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "WORKSPACE_EDIT_TOO_LARGE",
                        "编辑输入超过大小限制");
            }
            edits.add(new EditRequest(oldText.asText(), newText.asText(),
                    replaceAll != null && replaceAll.asBoolean()));
        }
        return List.copyOf(edits);
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private String replace(String value, String oldText, String newText, boolean replaceAll) {
        if (replaceAll) return value.replace(oldText, newText);
        int index = value.indexOf(oldText);
        return value.substring(0, index) + newText
                + value.substring(index + oldText.length());
    }

    private BusinessException invalidInput(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_INPUT_INVALID",
                definition().name() + " 输入无效: " + message);
    }

    private record EditRequest(String oldText, String newText, boolean replaceAll) {
    }
}
