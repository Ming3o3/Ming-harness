package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查看工作区范围内的 Git 状态。
 *
 * <p>这里只允许固定的只读 Git 子命令，并使用当前工作区作为路径范围，避免把仓库外的变更
 * 暴露给 Agent。未初始化 Git 的目录会返回明确的业务错误，而不会执行任意命令降级。</p>
 */
@Component
public class WorkspaceGitStatusTool implements HarnessTool {

    private static final int COMMAND_TIMEOUT_MS = 10_000;
    private static final int MAX_STATUS_OUTPUT_BYTES = 200_000;

    private final WorkspaceToolSupport support;
    private final WorkspaceCommandRunner commandRunner = new WorkspaceCommandRunner();

    public WorkspaceGitStatusTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.git.status",
                "查看工作区范围内的 Git 分支和文件变更状态；非 Git 工作区会返回可恢复的 available=false 结果，可改用 workspace.read",
                true,
                "LOW",
                false,
                Map.of("type", "object", "additionalProperties", false),
                Set.of("workspace.read"),
                COMMAND_TIMEOUT_MS,
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
        support.requireEnabled();
        support.parseObject(input, definition().name());
        Path root = support.resolve(".", false);
        Path gitDirectory;
        try {
            gitDirectory = support.requireGitDirectory();
        } catch (BusinessException exception) {
            if ("WORKSPACE_GIT_REPOSITORY_INVALID".equals(exception.getCode())) {
                return support.gitUnavailable(definition().name(), exception);
            }
            throw exception;
        }
        WorkspaceCommandRunner.Result result = commandRunner.run(
                List.of("git", "--git-dir=" + gitDirectory, "--work-tree=" + root,
                        "-c", "core.quotepath=false", "-c", "core.fsmonitor=false", "status",
                        "--porcelain=v1", "--branch", "--untracked-files=normal", "--", "."),
                root, COMMAND_TIMEOUT_MS, Math.min(MAX_STATUS_OUTPUT_BYTES,
                        support.properties().maxReadBytes()), "WORKSPACE_GIT");
        if (result.timedOut()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_GIT_TIMEOUT",
                    "Git 状态查询超时");
        }
        if (result.exitCode() == null || result.exitCode() != 0) {
            throw gitFailure(result.output());
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        int protectedEntryCount = 0;
        String branch = "—";
        String[] lines = result.output().split("\\R", -1);
        for (String line : lines) {
            if (line.startsWith("##")) {
                branch = line.length() > 3 ? line.substring(3).trim() : "—";
                continue;
            }
            if (line.length() < 3 || line.charAt(2) != ' ') continue;
            String path = line.substring(3);
            if (!isVisiblePath(path)) {
                // Git 不能绕过工作区工具层的隐藏路径边界；只保留数量以避免误报“干净”。
                protectedEntryCount++;
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", String.valueOf(line.charAt(0)));
            entry.put("worktree", String.valueOf(line.charAt(1)));
            entry.put("path", support.sanitize(path));
            entries.add(entry);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("branch", support.sanitize(branch));
        response.put("clean", entries.isEmpty() && protectedEntryCount == 0 && !result.outputTruncated());
        response.put("entries", entries);
        response.put("entryCount", entries.size() + protectedEntryCount);
        response.put("protectedEntryCount", protectedEntryCount);
        response.put("outputTruncated", result.outputTruncated());
        response.put("scope", ".");
        return support.json(response);
    }

    @Override
    public String execute(String input, ToolExecutionContext context) {
        return support.withWorkspace(context, () -> execute(input));
    }

    private BusinessException gitFailure(String output) {
        String detail = output == null || output.isBlank() ? "当前工作区不是 Git 仓库" : support.sanitize(output);
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_GIT_STATUS_FAILED",
                "Git 状态查询失败: " + detail);
    }

    /** Git 状态中的文件名也必须通过与读写工具一致的隐藏路径和符号链接校验。 */
    private boolean isVisiblePath(String path) {
        try {
            support.resolve(path, true);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }
}
