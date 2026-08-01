package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工作区命令的统一进程边界。
 *
 * <p>调用方必须先完成命令白名单或固定命令校验；本类只负责清空环境、收集输出和终止进程树，
 * 不接受 Shell 字符串，也不会把宿主机敏感环境变量传递给子进程。</p>
 */
final class WorkspaceCommandRunner {

    Result run(List<String> commandLine, Path workdir, int timeoutMs, int maxOutputBytes) {
        return run(commandLine, workdir, timeoutMs, maxOutputBytes, "WORKSPACE_COMMAND");
    }

    Result run(List<String> commandLine, Path workdir, int timeoutMs, int maxOutputBytes,
               String errorCodePrefix) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine)
                    .directory(workdir.toFile())
                    .redirectErrorStream(true);
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
            return new Result(exitCode, !finished, collector.outputTruncated(), collector.output());
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, errorCodePrefix + "_START_FAILED",
                    "无法启动工作区命令");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, errorCodePrefix + "_INTERRUPTED",
                    "工作区命令执行被中断");
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

    record Result(Integer exitCode, boolean timedOut, boolean outputTruncated, String output) {
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
