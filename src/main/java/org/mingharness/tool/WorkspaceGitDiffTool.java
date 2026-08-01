package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 查看工作区范围内的 Git diff，帮助 Agent 在测试前确认实际代码改动。 */
@Component
public class WorkspaceGitDiffTool implements HarnessTool {

    private static final int COMMAND_TIMEOUT_MS = 10_000;

    private final WorkspaceToolSupport support;
    private final WorkspaceCommandRunner commandRunner = new WorkspaceCommandRunner();

    public WorkspaceGitDiffTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.git.diff",
                "查看工作区范围内的 Git 未暂存或已暂存差异",
                true,
                "LOW",
                false,
                Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "path", Map.of("type", "string", "minLength", 1),
                                "staged", Map.of("type", "boolean"),
                                "contextLines", Map.of("type", "integer", "minimum", 0, "maximum", 100),
                                "maxOutputBytes", Map.of("type", "integer", "minimum", 1_024)
                        )
                ),
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
        JsonNode request = support.parseObject(input, definition().name());
        String rawPath = support.optionalText(request, "path");
        Path root = support.resolve(".", false);
        Path gitDirectory = support.requireGitDirectory();
        String relativePath = ".";
        if (rawPath != null && !rawPath.isBlank()) {
            Path target = support.resolve(rawPath, true);
            relativePath = support.relative(target);
            if (relativePath.isBlank()) relativePath = ".";
        }
        boolean staged = support.optionalBoolean(request, "staged", false, definition().name());
        int contextLines = support.optionalInt(request, "contextLines", 3, 0, 100, definition().name());
        int maxOutputBytes = support.optionalInt(request, "maxOutputBytes",
                Math.min(support.properties().maxReadBytes(), 500_000), 1_024,
                support.properties().maxReadBytes(), definition().name());
        List<String> command = new java.util.ArrayList<>(10);
        command.add("git");
        command.add("--git-dir=" + gitDirectory);
        command.add("--work-tree=" + root);
        command.add("-c");
        command.add("core.quotepath=false");
        command.add("-c");
        command.add("core.fsmonitor=false");
        command.add("diff");
        command.add("--no-ext-diff");
        command.add("--no-textconv");
        command.add("--unified=" + contextLines);
        if (staged) command.add("--cached");
        command.add("--");
        command.add(relativePath);

        WorkspaceCommandRunner.Result result = commandRunner.run(command, root, COMMAND_TIMEOUT_MS,
                maxOutputBytes, "WORKSPACE_GIT");
        if (result.timedOut()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_GIT_TIMEOUT",
                    "Git 差异查询超时");
        }
        if (result.exitCode() == null || result.exitCode() != 0) {
            String detail = result.output() == null || result.output().isBlank()
                    ? "当前工作区不是 Git 仓库" : support.sanitize(result.output());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_GIT_DIFF_FAILED",
                    "Git 差异查询失败: " + detail);
        }

        String diff = support.sanitize(result.output());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("path", relativePath);
        response.put("staged", staged);
        response.put("contextLines", contextLines);
        response.put("hasChanges", !diff.isBlank());
        response.put("outputTruncated", result.outputTruncated());
        response.put("outputBytes", diff.getBytes(StandardCharsets.UTF_8).length);
        response.put("diff", diff);
        return support.json(response);
    }
}
