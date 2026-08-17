package org.mingharness.education;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.CodeEvaluationProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Docker 进行只读语法/编译检查。
 *
 * <p>该适配器不会在宿主机执行学生代码，也不会自动拉取镜像；容器关闭网络、只读挂载源文件，
 * 并限制内存、CPU、进程数、临时目录和输出大小。完整单元测试评测可在此接口上继续扩展，
 * 但教师确认仍必须基于提交物和学习证据，而不能把编译通过直接当作教学结论。</p>
 */
@Component
public class DockerEducationCodeEvaluator implements EducationCodeEvaluator {

    private static final Map<String, String> SOURCE_FILES = Map.ofEntries(
            Map.entry("PYTHON", "main.py"), Map.entry("PY", "main.py"),
            Map.entry("JAVASCRIPT", "main.js"), Map.entry("JS", "main.js"),
            Map.entry("NODE", "main.js"), Map.entry("JAVA", "Main.java"),
            Map.entry("C", "main.c"), Map.entry("CPP", "main.cpp"),
            Map.entry("C++", "main.cpp"), Map.entry("CXX", "main.cpp"),
            Map.entry("GO", "main.go"), Map.entry("GOLANG", "main.go"),
            Map.entry("RUST", "main.rs"), Map.entry("RS", "main.rs")
    );

    private final CodeEvaluationProperties properties;
    private final SensitiveDataSanitizer sanitizer;

    public DockerEducationCodeEvaluator(CodeEvaluationProperties properties,
                                        SensitiveDataSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    public EducationCodeEvaluationResult evaluate(String programmingLanguage, String sourceCode) {
        String language = properties.normalizedLanguage(programmingLanguage);
        String fileName = SOURCE_FILES.get(language);
        if (fileName == null) {
            return new EducationCodeEvaluationResult(CodeEvaluationStatus.REJECTED,
                    "当前仅支持 Python、JavaScript、Java、C、C++、Go 和 Rust 的语法/编译检查。",
                    "", null, 0);
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            return new EducationCodeEvaluationResult(CodeEvaluationStatus.REJECTED,
                    "代码内容不能为空。", "", null, 0);
        }
        int sourceBytes = sourceCode.getBytes(StandardCharsets.UTF_8).length;
        if (sourceBytes > properties.maxSourceBytes()) {
            return new EducationCodeEvaluationResult(CodeEvaluationStatus.REJECTED,
                    "代码大小超过评测限制。", "", null, 0);
        }
        if (!properties.enabled()) {
            return EducationCodeEvaluationResult.unavailable("代码评测沙箱未启用，请联系管理员配置独立容器运行环境。");
        }

        Path root = Path.of(properties.temporaryRoot()).toAbsolutePath().normalize();
        Path temporaryDirectory = null;
        Instant startedAt = Instant.now();
        try {
            Files.createDirectories(root);
            temporaryDirectory = Files.createTempDirectory(root, "education-code-");
            Files.writeString(temporaryDirectory.resolve(fileName), sourceCode,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            List<String> command = dockerCommand(language, temporaryDirectory, fileName);
            ProcessResult result = run(command);
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            CodeEvaluationStatus status = result.timedOut()
                    ? CodeEvaluationStatus.TIMEOUT
                    : (result.exitCode() != null && result.exitCode() == 0
                    ? CodeEvaluationStatus.PASSED : CodeEvaluationStatus.FAILED);
            String output = sanitizer.sanitize(result.output());
            String diagnostics = status == CodeEvaluationStatus.PASSED
                    ? "语法/编译检查通过。" : trimDiagnostic(output);
            return new EducationCodeEvaluationResult(status, diagnostics, output,
                    result.exitCode(), durationMs);
        } catch (IOException exception) {
            return new EducationCodeEvaluationResult(CodeEvaluationStatus.UNAVAILABLE,
                    "无法启动代码评测沙箱，请检查 Docker 服务和对应运行镜像。", "", null,
                    Duration.between(startedAt, Instant.now()).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new EducationCodeEvaluationResult(CodeEvaluationStatus.ERROR,
                    "代码评测被系统中断。", "", null,
                    Duration.between(startedAt, Instant.now()).toMillis());
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    private List<String> dockerCommand(String language, Path directory, String fileName) {
        String image = properties.imageFor(language);
        List<String> command = new ArrayList<>(List.of(
                properties.dockerExecutable(), "run", "--rm", "--pull=never",
                "--network=none",
                "--memory=" + properties.memoryMb() + "m",
                "--cpus=" + properties.cpus(),
                "--pids-limit=" + properties.pidsLimit(),
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--mount", "type=bind,src=" + directory + ",dst=/workspace,readonly",
                image));
        switch (properties.normalizedLanguage(language)) {
            case "PYTHON", "PY" -> command.addAll(List.of("python", "-m", "py_compile",
                    "/workspace/" + fileName));
            case "JAVASCRIPT", "JS", "NODE" -> command.addAll(List.of("node", "--check",
                    "/workspace/" + fileName));
            case "JAVA" -> command.addAll(List.of("javac", "-d", "/tmp/classes",
                    "/workspace/" + fileName));
            case "C" -> command.addAll(List.of("gcc", "-fsyntax-only", "/workspace/" + fileName));
            case "CPP", "C++", "CXX" -> command.addAll(List.of("g++", "-fsyntax-only",
                    "/workspace/" + fileName));
            case "GO", "GOLANG" -> command.addAll(List.of("go", "test", "/workspace/" + fileName));
            case "RUST", "RS" -> command.addAll(List.of("rustc", "--emit=metadata", "-o",
                    "/tmp/main.rmeta", "/workspace/" + fileName));
            default -> throw new IllegalArgumentException("unsupported language");
        }
        return List.copyOf(command);
    }

    private ProcessResult run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.clear();
        String path = System.getenv("PATH");
        if (path != null && !path.isBlank()) environment.put("PATH", path);
        environment.put("LANG", "C.UTF-8");
        Process process = builder.start();
        BoundedOutput output = new BoundedOutput(process, properties.maxOutputBytes());
        Thread reader = new Thread(output, "harness-code-evaluation-output");
        reader.setDaemon(true);
        reader.start();
        boolean finished = process.waitFor(properties.timeoutMs(), TimeUnit.MILLISECONDS);
        if (!finished) destroyProcess(process);
        reader.join(Math.min(properties.timeoutMs(), 2_000L));
        if (reader.isAlive()) {
            destroyProcess(process);
            reader.interrupt();
        }
        if (!finished) process.waitFor(1, TimeUnit.SECONDS);
        Integer exitCode = finished && !process.isAlive() ? process.exitValue() : null;
        return new ProcessResult(exitCode, !finished, output.output());
    }

    private String trimDiagnostic(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return "代码检查未通过，但沙箱没有返回诊断信息。";
        return normalized.length() > 2_000 ? normalized.substring(0, 2_000) + "…" : normalized;
    }

    private void deleteRecursively(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不应覆盖已经产生的评测结果。
                }
            });
        } catch (IOException ignored) {
            // 同上。
        }
    }

    private static void destroyProcess(Process process) {
        try {
            process.toHandle().descendants().forEach(handle -> {
                handle.destroy();
                if (handle.isAlive()) handle.destroyForcibly();
            });
        } catch (RuntimeException ignored) {
            // 继续销毁主进程。
        }
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
    }

    private record ProcessResult(Integer exitCode, boolean timedOut, String output) {
    }

    private static final class BoundedOutput implements Runnable {
        private final Process process;
        private final int maxBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile boolean truncated;

        private BoundedOutput(Process process, int maxBytes) {
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
                        truncated = true;
                        destroyProcess(process);
                        break;
                    }
                    output.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // 进程超时或输出达到上限时关闭管道属于预期路径。
            }
        }

        private String output() {
            String value = output.toString(StandardCharsets.UTF_8);
            return truncated ? value + "\n[评测输出已截断]" : value;
        }
    }
}
