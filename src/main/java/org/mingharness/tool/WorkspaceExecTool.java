package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在工作区内执行受控命令。
 *
 * <p>命令使用 {@link ProcessBuilder} 的参数列表直接启动，绝不经过 Shell；可执行文件必须
 * 命中显式白名单，工作目录必须位于工作区内，子进程环境会清空并只保留最小运行变量。该工具
 * 始终需要人工审批，即使调用方已经拥有 workspace.exec 权限。</p>
 */
@Component
public class WorkspaceExecTool implements HarnessTool {

    private static final int MAX_COMMAND_LENGTH = 8_000;
    private static final int MAX_ARGUMENT_LENGTH = 4_000;

    private final WorkspaceToolSupport support;

    public WorkspaceExecTool(WorkspaceToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "workspace.exec",
                "在工作区内按白名单执行单个命令，返回退出码和受限输出",
                false,
                "HIGH",
                true,
                Map.of(
                        "type", "object",
                        "required", List.of("command"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "command", Map.of("type", "string", "minLength", 1, "maxLength", 512),
                                "args", Map.of(
                                        "type", "array",
                                        "maxItems", 128,
                                        "items", Map.of("type", "string", "maxLength", MAX_ARGUMENT_LENGTH)
                                ),
                                "workdir", Map.of("type", "string", "minLength", 1),
                                "timeoutMs", Map.of("type", "integer", "minimum", 1),
                                "maxOutputBytes", Map.of("type", "integer", "minimum", 1024)
                        )
                ),
                Set.of("workspace.exec"),
                600_000,
                1,
                "DENY_EXTERNAL",
                Map.of("type", "object")
        );
    }

    @Override
    public boolean available() {
        return support.properties().enabled() && support.properties().execEnabled();
    }

    @Override
    public String execute(String input) {
        support.requireExecEnabled();
        JsonNode request = support.parseObject(input, definition().name());
        String command = support.requiredText(request, "command", definition().name());
        validateToken(command, "command");
        if (!support.properties().allowedCommands().contains(command)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "WORKSPACE_COMMAND_NOT_ALLOWED",
                    "命令不在工作区白名单中: " + command);
        }

        List<String> args = parseArguments(request.get("args"));
        String rawWorkdir = support.optionalText(request, "workdir");
        Path workdir = support.resolve(rawWorkdir == null || rawWorkdir.isBlank() ? "." : rawWorkdir, false);
        if (!Files.isDirectory(workdir)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_EXEC_WORKDIR_INVALID",
                    "命令工作目录不是目录: " + (rawWorkdir == null ? "." : rawWorkdir));
        }

        int timeoutMs = support.optionalInt(request, "timeoutMs", Math.min(
                support.properties().maxCommandTimeoutMs(), 30_000), 1,
                support.properties().maxCommandTimeoutMs(), definition().name());
        int maxOutputBytes = support.optionalInt(request, "maxOutputBytes",
                support.properties().maxCommandOutputBytes(), 1_024,
                support.properties().maxCommandOutputBytes(), definition().name());
        List<String> commandLine = new ArrayList<>(1 + args.size());
        commandLine.add(command);
        commandLine.addAll(args);
        int totalLength = commandLine.stream().mapToInt(String::length).sum() + commandLine.size();
        if (totalLength > MAX_COMMAND_LENGTH) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "WORKSPACE_COMMAND_TOO_LARGE",
                    "命令和参数总长度超过限制");
        }

        Instant startedAt = Instant.now();
        ProcessResult result = runProcess(commandLine, workdir, timeoutMs, maxOutputBytes);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("command", support.sanitize(command));
        response.put("args", args.stream().map(support::sanitize).toList());
        String relativeWorkdir = support.relative(workdir);
        response.put("workdir", relativeWorkdir.isBlank() ? "." : relativeWorkdir);
        response.put("exitCode", result.exitCode());
        response.put("timedOut", result.timedOut());
        response.put("outputTruncated", result.outputTruncated());
        response.put("outputBytes", result.output().getBytes(StandardCharsets.UTF_8).length);
        response.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
        response.put("ok", !result.timedOut() && !result.outputTruncated()
                && result.exitCode() != null && result.exitCode() == 0);
        response.put("output", support.sanitize(result.output()));
        return support.json(response);
    }

    @Override
    public ToolAudit audit(String input, String output) {
        JsonNode result = support.parseObject(output, definition().name());
        String command = support.optionalText(result, "command");
        String workdir = support.optionalText(result, "workdir");
        JsonNode exitCode = result.get("exitCode");
        String exit = exitCode == null || exitCode.isNull() ? "null" : exitCode.asText();
        String message = "工作区命令执行: " + (command == null ? "未知命令" : command)
                + "，退出码=" + exit + "，工作目录=" + (workdir == null ? "." : workdir);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("command", command);
        metadata.put("args", result.get("args"));
        metadata.put("workdir", workdir);
        metadata.put("exitCode", exitCode);
        metadata.put("timedOut", result.path("timedOut").asBoolean(false));
        metadata.put("outputTruncated", result.path("outputTruncated").asBoolean(false));
        metadata.put("outputBytes", result.path("outputBytes").asInt(0));
        metadata.put("durationMs", result.path("durationMs").asLong(0));
        metadata.put("ok", result.path("ok").asBoolean(false));
        return new ToolAudit("WORKSPACE_COMMAND_EXECUTED", message, support.json(metadata));
    }

    private List<String> parseArguments(JsonNode rawArgs) {
        if (rawArgs == null || rawArgs.isNull()) return List.of();
        if (!rawArgs.isArray() || rawArgs.size() > support.properties().maxCommandArgs()) {
            throw invalidInput("args 必须是最多 " + support.properties().maxCommandArgs() + " 个字符串");
        }
        List<String> args = new ArrayList<>(rawArgs.size());
        for (JsonNode item : rawArgs) {
            if (!item.isTextual()) throw invalidInput("args 中每一项必须是字符串");
            String value = item.asText();
            validateToken(value, "args");
            if (value.length() > MAX_ARGUMENT_LENGTH) throw invalidInput("args 单项长度超过限制");
            args.add(value);
        }
        return List.copyOf(args);
    }

    private void validateToken(String value, String field) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0
                || value.chars().anyMatch(character -> character < 0x20 && character != '\t')) {
            throw invalidInput(field + " 不能包含空值或控制字符");
        }
    }

    private ProcessResult runProcess(List<String> commandLine, Path workdir,
                                     int timeoutMs, int maxOutputBytes) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine)
                    .directory(workdir.toFile())
                    .redirectErrorStream(true);
            // 清空环境，避免把模型密钥、数据库密码和其他宿主机凭证传给子进程。
            Map<String, String> environment = builder.environment();
            environment.clear();
            copySafeEnvironment(environment, "PATH");
            copySafeEnvironment(environment, "LANG");
            copySafeEnvironment(environment, "LC_ALL");
            environment.put("JAVA_HOME", System.getProperty("java.home", ""));
            environment.put("TMPDIR", System.getProperty("java.io.tmpdir", "/tmp"));

            Process process = builder.start();
            OutputCollector collector = new OutputCollector(process, maxOutputBytes);
            Thread reader = new Thread(collector, "harness-workspace-output");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) destroyProcessTree(process);
            reader.join(Math.min(timeoutMs, 5_000L));
            if (reader.isAlive()) {
                destroyProcessTree(process);
                reader.interrupt();
            }
            if (!finished) process.waitFor(2, TimeUnit.SECONDS);
            Integer exitCode = finished && !process.isAlive() ? process.exitValue() : null;
            return new ProcessResult(exitCode, !finished, collector.outputTruncated(), collector.output());
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_EXEC_START_FAILED",
                    "无法启动白名单命令");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "WORKSPACE_EXEC_INTERRUPTED",
                    "命令执行被中断");
        }
    }

    private void copySafeEnvironment(Map<String, String> environment, String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) environment.put(name, value);
    }

    private static void destroyProcessTree(Process process) {
        try {
            List<ProcessHandle> descendants = process.toHandle().descendants().toList();
            for (int index = descendants.size() - 1; index >= 0; index--) {
                ProcessHandle handle = descendants.get(index);
                handle.destroy();
                if (handle.isAlive()) handle.destroyForcibly();
            }
        } catch (RuntimeException ignored) {
            // 即使子进程枚举失败，也必须继续尝试销毁主进程。
        }
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
    }

    private BusinessException invalidInput(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_INPUT_INVALID",
                definition().name() + " 输入无效: " + message);
    }

    private record ProcessResult(Integer exitCode, boolean timedOut,
                                 boolean outputTruncated, String output) {
    }

    private static final class OutputCollector implements Runnable {
        private final Process process;
        private final int maxBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile boolean outputTruncated;

        private OutputCollector(Process process, int maxBytes) {
            this.process = process;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[8_192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int remaining = maxBytes - output.size();
                    if (read > remaining) {
                        if (remaining > 0) output.write(buffer, 0, remaining);
                        outputTruncated = true;
                        destroyProcessTree(process);
                        break;
                    }
                    output.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // 进程被超时或输出上限主动销毁时，管道关闭属于预期路径。
            }
        }

        private String output() {
            return output.toString(StandardCharsets.UTF_8);
        }

        private boolean outputTruncated() {
            return outputTruncated;
        }
    }
}
