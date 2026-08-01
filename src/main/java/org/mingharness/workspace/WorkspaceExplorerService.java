package org.mingharness.workspace;

import org.mingharness.common.BusinessException;
import org.mingharness.tool.WorkspaceGitDiffTool;
import org.mingharness.tool.WorkspaceGitStatusTool;
import org.mingharness.tool.WorkspaceToolSupport;
import org.mingharness.workspace.api.WorkspaceExplorerEntryView;
import org.mingharness.workspace.api.WorkspaceExplorerView;
import org.mingharness.workspace.api.WorkspaceFileContentView;
import org.mingharness.workspace.api.WorkspaceGitChangeView;
import org.mingharness.workspace.api.WorkspaceGitDiffView;
import org.mingharness.workspace.api.WorkspaceGitOverviewView;
import org.mingharness.workspace.api.WorkspaceGitStatusView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 为桌面控制台提供只读项目浏览能力。
 *
 * <p>这里复用 {@link WorkspaceToolSupport} 的路径、隐藏文件、符号链接和 UTF-8 边界；浏览器
 * 不会直接向本机文件系统发请求，也不会得到绝对路径。</p>
 */
@Service
public class WorkspaceExplorerService {

    private static final int MAX_PATH_LENGTH = 1_024;

    private final WorkspaceDirectoryService workspaceDirectoryService;
    private final WorkspaceToolSupport workspace;
    private final WorkspaceGitStatusTool gitStatusTool;
    private final WorkspaceGitDiffTool gitDiffTool;
    private final ObjectMapper objectMapper;

    public WorkspaceExplorerService(WorkspaceDirectoryService workspaceDirectoryService,
                                    WorkspaceToolSupport workspace,
                                    WorkspaceGitStatusTool gitStatusTool,
                                    WorkspaceGitDiffTool gitDiffTool,
                                    ObjectMapper objectMapper) {
        this.workspaceDirectoryService = workspaceDirectoryService;
        this.workspace = workspace;
        this.gitStatusTool = gitStatusTool;
        this.gitDiffTool = gitDiffTool;
        this.objectMapper = objectMapper;
    }

    /** 列出当前会话绑定工作区的一层目录，并附带不影响主功能的 Git 摘要。 */
    public WorkspaceExplorerView browse(String workspaceId, String tenantId, String userId, String rawPath) {
        Path root = workspaceDirectoryService.requireRoot(normalizeWorkspaceId(workspaceId), tenantId, userId);
        return workspace.withRoot(root, () -> browseInsideRoot(normalizePath(rawPath)));
    }

    /** 读取受限文本预览；内容经过凭证脱敏，防止项目内误提交密钥进入页面缓存。 */
    public WorkspaceFileContentView read(String workspaceId, String tenantId, String userId, String rawPath) {
        Path root = workspaceDirectoryService.requireRoot(normalizeWorkspaceId(workspaceId), tenantId, userId);
        return workspace.withRoot(root, () -> readInsideRoot(requiredPath(rawPath)));
    }

    /** 返回当前项目全部可见变更，供用户在 Agent 执行后快速审阅。 */
    public WorkspaceGitStatusView gitStatus(String workspaceId, String tenantId, String userId) {
        Path root = workspaceDirectoryService.requireRoot(normalizeWorkspaceId(workspaceId), tenantId, userId);
        return workspace.withRoot(root, this::gitStatusInsideRoot);
    }

    /** 返回单个相对路径的已暂存或工作区 Diff，输出仍经过工具层的脱敏和大小限制。 */
    public WorkspaceGitDiffView gitDiff(String workspaceId, String tenantId, String userId,
                                        String rawPath, boolean staged, int contextLines) {
        Path root = workspaceDirectoryService.requireRoot(normalizeWorkspaceId(workspaceId), tenantId, userId);
        String path = normalizePath(rawPath);
        int lines = normalizeContextLines(contextLines);
        return workspace.withRoot(root, () -> gitDiffInsideRoot(path, staged, lines));
    }

    private WorkspaceExplorerView browseInsideRoot(String requestedPath) {
        Path directory = workspace.resolve(requestedPath, false);
        String path = relative(directory);
        List<WorkspaceExplorerEntryView> entries = workspace.list(directory, path, false).stream()
                .map(this::entryView).toList();
        return new WorkspaceExplorerView(path, parentPath(path), entries, gitOverview());
    }

    private WorkspaceFileContentView readInsideRoot(String requestedPath) {
        Path file = workspace.resolve(requestedPath, false);
        String path = relative(file);
        String original = workspace.readText(file, path);
        String[] lines = original.split("\\R", -1);
        int endLine = Math.min(lines.length, workspace.properties().maxReadLines());
        String preview = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, endLine));
        String sanitized = workspace.sanitize(preview);
        return new WorkspaceFileContentView(path, sanitized,
                workspace.sha256(original.getBytes(StandardCharsets.UTF_8)), lines.length,
                endLine < lines.length, !sanitized.equals(preview));
    }

    private WorkspaceGitStatusView gitStatusInsideRoot() {
        JsonNode status = parseGitResult(gitStatusTool.execute("{}"), "WORKSPACE_GIT_STATUS_INVALID");
        List<WorkspaceGitChangeView> entries = new java.util.ArrayList<>();
        JsonNode rawEntries = status.path("entries");
        if (rawEntries.isArray()) {
            for (JsonNode entry : rawEntries) {
                String path = entry.path("path").asText("");
                if (path.isBlank()) continue;
                entries.add(new WorkspaceGitChangeView(entry.path("index").asText(" "),
                        entry.path("worktree").asText(" "), path));
            }
        }
        return new WorkspaceGitStatusView(status.path("branch").asText("—"),
                status.path("clean").asBoolean(entries.isEmpty()), List.copyOf(entries),
                status.path("entryCount").asInt(entries.size()), status.path("protectedEntryCount").asInt(0),
                status.path("outputTruncated").asBoolean(false));
    }

    private WorkspaceGitDiffView gitDiffInsideRoot(String path, boolean staged, int contextLines) {
        String input = workspace.json(Map.of("path", path, "staged", staged, "contextLines", contextLines));
        JsonNode diff = parseGitResult(gitDiffTool.execute(input), "WORKSPACE_GIT_DIFF_INVALID");
        return new WorkspaceGitDiffView(diff.path("path").asText(path), diff.path("staged").asBoolean(staged),
                diff.path("contextLines").asInt(contextLines), diff.path("hasChanges").asBoolean(false),
                diff.path("outputTruncated").asBoolean(false), diff.path("outputBytes").asInt(0),
                diff.path("diff").asText(""));
    }

    private WorkspaceExplorerEntryView entryView(Map<String, Object> entry) {
        String path = String.valueOf(entry.getOrDefault("path", ""));
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        boolean directory = "directory".equals(entry.get("type"));
        Object rawSize = entry.get("size");
        Long size = rawSize instanceof Number number ? number.longValue() : null;
        return new WorkspaceExplorerEntryView(path, name, directory, size);
    }

    private WorkspaceGitOverviewView gitOverview() {
        try {
            String raw = gitStatusTool.execute("{}");
            JsonNode status = objectMapper.readTree(raw);
            return new WorkspaceGitOverviewView(true, status.path("branch").asText("—"),
                    status.path("clean").asBoolean(false), status.path("entryCount").asInt(0),
                    status.path("outputTruncated").asBoolean(false));
        } catch (BusinessException exception) {
            // 非 Git 目录、Git 不可执行等不应影响用户查看普通项目文件。
            return WorkspaceGitOverviewView.unavailable();
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "WORKSPACE_GIT_STATUS_INVALID",
                    "Git 状态结果无法解析");
        }
    }

    private JsonNode parseGitResult(String raw, String errorCode) {
        try {
            JsonNode result = objectMapper.readTree(raw);
            if (result == null || !result.isObject()) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, "Git 工具结果不是对象");
            }
            return result;
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, "Git 工具结果无法解析");
        }
    }

    private String normalizeWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) return null;
        if (workspaceId.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_ID_INVALID", "工作区标识长度不能超过 128 个字符");
        }
        return workspaceId;
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return ".";
        if (rawPath.length() > MAX_PATH_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_PATH_TOO_LONG", "工作区相对路径过长");
        }
        return rawPath;
    }

    private String requiredPath(String rawPath) {
        String path = normalizePath(rawPath);
        if (".".equals(path)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_FILE_PATH_REQUIRED", "请选择要预览的文件");
        }
        return path;
    }

    private int normalizeContextLines(int contextLines) {
        if (contextLines < 0 || contextLines > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_GIT_CONTEXT_INVALID",
                    "Git Diff 上下文行数必须在 0 到 100 之间");
        }
        return contextLines;
    }

    private String relative(Path path) {
        String value = workspace.relative(path);
        return value.isBlank() ? "." : value;
    }

    private String parentPath(String path) {
        if (path == null || path.isBlank() || ".".equals(path) || !path.contains("/")) return ".";
        return path.substring(0, path.lastIndexOf('/'));
    }
}
