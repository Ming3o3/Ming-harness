package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.WorkspaceProperties;
import org.mingharness.workspace.WorkspaceDirectoryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.InvalidPathException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 工作区工具共享的安全边界和 JSON 辅助方法。
 *
 * <p>所有路径先做 normalize，再检查真实路径是否仍位于工作区根目录内；既拒绝 {@code ..}
 * 穿越，也拒绝指向工作区外的符号链接。这里不提供任意 Shell 能力，避免工具实现绕过策略。</p>
 */
@Component
public class WorkspaceToolSupport {

    private static final Set<String> RECOVERABLE_READ_FAILURES = Set.of(
            "WORKSPACE_PATH_NOT_FOUND",
            "WORKSPACE_NOT_DIRECTORY",
            "WORKSPACE_NOT_FILE",
            "WORKSPACE_FILE_TOO_LARGE",
            "WORKSPACE_BINARY_FILE",
            "WORKSPACE_HIDDEN_PATH_DENIED",
            "WORKSPACE_PATH_DENIED",
            "WORKSPACE_IO_FAILED"
    );

    private final WorkspaceProperties properties;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sanitizer;
    private final WorkspaceDirectoryResolver workspaceDirectoryResolver;
    private final Path configuredRoot;
    /** 每个 Worker 线程在一次工具调用中绑定专属根目录，绝不修改其他 Run 的工作区。 */
    private final ThreadLocal<Path> executionRoot = new ThreadLocal<>();

    public WorkspaceToolSupport(WorkspaceProperties properties,
                                ObjectMapper objectMapper,
                                SensitiveDataSanitizer sanitizer) {
        this(properties, objectMapper, sanitizer, null);
    }

    @Autowired
    public WorkspaceToolSupport(WorkspaceProperties properties,
                                ObjectMapper objectMapper,
                                SensitiveDataSanitizer sanitizer,
                                WorkspaceDirectoryResolver workspaceDirectoryResolver) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sanitizer = sanitizer;
        this.workspaceDirectoryResolver = workspaceDirectoryResolver;
        this.configuredRoot = Path.of(properties.root()).toAbsolutePath().normalize();
    }

    public WorkspaceProperties properties() {
        return properties;
    }

    public Path root() {
        Path scoped = executionRoot.get();
        return scoped == null ? configuredRoot : scoped;
    }

    /** 在当前工具调用线程内临时绑定 Run 所属的工作区，完成后必须恢复原上下文。 */
    public <T> T withWorkspace(ToolExecutionContext context, Supplier<T> action) {
        Path root = workspaceDirectoryResolver == null
                ? configuredRoot : workspaceDirectoryResolver.resolve(context);
        return withRoot(root, action);
    }

    /** 聊天附件导入等非工具流程复用同一条路径边界，不会影响并行 Run。 */
    public <T> T withRoot(Path requestedRoot, Supplier<T> action) {
        Path normalized = requestedRoot.toAbsolutePath().normalize();
        Path previous = executionRoot.get();
        executionRoot.set(normalized);
        try {
            return action.get();
        } finally {
            if (previous == null) executionRoot.remove();
            else executionRoot.set(previous);
        }
    }

    public void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "WORKSPACE_DISABLED",
                    "工作区工具未启用，请配置 HARNESS_WORKSPACE_ROOT 和 WORKSPACE_ENABLED=true");
        }
    }

    /** 命令执行单独受开关保护，读取/写入工具启用不代表允许启动子进程。 */
    public void requireExecEnabled() {
        requireEnabled();
        if (!properties.execEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "WORKSPACE_EXEC_DISABLED",
                    "工作区命令执行未启用，请配置 WORKSPACE_EXEC_ENABLED=true");
        }
    }

    /**
     * Git 工具只接受工作区根目录下的普通 .git 目录，避免仓库配置把工作树指向边界之外。
     * 采用独立 Git worktree（.git 文件）时应将工作区根目录配置为主仓库目录。
     */
    public Path requireGitDirectory() {
        requireEnabled();
        Path root = root();
        Path gitDirectory = root.resolve(".git");
        if (Files.isSymbolicLink(gitDirectory)
                || !Files.isDirectory(gitDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_GIT_REPOSITORY_INVALID",
                    "工作区根目录必须是包含普通 .git 目录的 Git 仓库");
        }
        return gitDirectory;
    }

    /** 解析工作区内的路径，并对已有路径的真实位置做二次校验。 */
    public Path resolve(String rawPath, boolean allowMissing) {
        requireEnabled();
        Path root = root();
        String value = rawPath == null || rawPath.isBlank() ? "." : rawPath.trim();
        Path requested;
        try {
            requested = Path.of(value);
        } catch (InvalidPathException exception) {
            throw invalidPath(value);
        }
        if (value.indexOf('\0') >= 0 || requested.isAbsolute()) {
            throw invalidPath(value);
        }
        Path candidate = root.resolve(value).normalize();
        if (!candidate.startsWith(root)) {
            throw invalidPath(value);
        }
        checkHiddenSegments(candidate);
        ensureRootDirectory(root);
        checkNoSymlinkSegments(candidate, value);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            ensureWithinRoot(realPath(candidate, value));
            return candidate;
        }
        if (!allowMissing) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "WORKSPACE_PATH_NOT_FOUND",
                    "工作区路径不存在: " + value);
        }
        // 对不存在的目标，沿父目录向上找到最近的真实目录，阻断中间目录的外部符号链接。
        Path existingParent = candidate.getParent();
        while (existingParent != null
                && !Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
            existingParent = existingParent.getParent();
        }
        if (existingParent == null) {
            throw invalidPath(value);
        }
        ensureWithinRoot(realPath(existingParent, value));
        return candidate;
    }

    public JsonNode parseObject(String rawInput, String toolName) {
        if (rawInput == null || rawInput.isBlank()) {
            throw invalidInput(toolName, "工具输入不能为空");
        }
        try {
            JsonNode node = objectMapper.reader().readTree(rawInput);
            if (node == null || !node.isObject()) {
                throw invalidInput(toolName, "工具输入必须是 JSON 对象");
            }
            return node;
        } catch (JacksonException exception) {
            throw invalidInput(toolName, "工具输入必须是有效 JSON");
        }
    }

    public String requiredText(JsonNode input, String field, String toolName) {
        JsonNode value = input.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalidInput(toolName, "字段 " + field + " 必须是非空字符串");
        }
        return value.asText();
    }

    public String optionalText(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public int optionalInt(JsonNode input, String field, int defaultValue, int min, int max,
                           String toolName) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || value.asInt() < min || value.asInt() > max) {
            throw invalidInput(toolName, "字段 " + field + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return value.asInt();
    }

    public boolean optionalBoolean(JsonNode input, String field, boolean defaultValue,
                                   String toolName) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw invalidInput(toolName, "字段 " + field + " 必须是布尔值");
        }
        return value.asBoolean();
    }

    public String readText(Path path, String displayPath) {
        ensureRegularFile(path, displayPath);
        try {
            long size = Files.size(path);
            if (size > properties.maxReadBytes()) {
                throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "WORKSPACE_FILE_TOO_LARGE",
                        "文件超过读取大小限制: " + displayPath);
            }
            byte[] bytes = Files.readAllBytes(path);
            return decodeUtf8(bytes, displayPath);
        } catch (IOException exception) {
            throw ioFailure("读取工作区文件失败", displayPath, exception);
        }
    }

    public String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }

    public String writeText(Path path, String displayPath, String content, String expectedSha256) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.maxWriteBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "WORKSPACE_WRITE_TOO_LARGE",
                    "写入内容超过大小限制: " + displayPath);
        }
        try {
            boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
            if (exists && Files.isSymbolicLink(path)) {
                throw invalidPath(displayPath);
            }
            if (exists && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_TARGET_NOT_FILE",
                        "写入目标不是普通文件: " + displayPath);
            }
            if (!exists && expectedSha256 != null && !expectedSha256.isBlank()) {
                throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_FILE_CHANGED",
                        "文件在读取后已被删除，请重新读取后再写入");
            }
            if (exists && (expectedSha256 == null || expectedSha256.isBlank())) {
                throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_EXPECTED_HASH_REQUIRED",
                        "覆盖已有文件必须提供 expectedSha256");
            }
            if (exists && !sha256(Files.readAllBytes(path)).equalsIgnoreCase(expectedSha256.trim())) {
                throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_FILE_CHANGED",
                        "文件在读取后已发生变化，请重新读取后再写入");
            }
            Path parent = path.getParent();
            if (parent == null) {
                throw invalidPath(displayPath);
            }
            createDirectoriesInsideRoot(parent);
            Path temp = Files.createTempFile(parent, ".harness-write-", ".tmp");
            try {
                Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            return sha256(bytes);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw ioFailure("写入工作区文件失败", displayPath, exception);
        }
    }

    public List<Map<String, Object>> list(Path directory, String displayPath, boolean recursive) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_NOT_DIRECTORY",
                    "工作区路径不是目录: " + displayPath);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            if (!recursive) {
                try (Stream<Path> stream = Files.list(directory)) {
                    stream.sorted().limit(properties.maxListEntries()).forEach(path -> addEntry(result, path));
                }
            } else {
                try (Stream<Path> stream = Files.walk(directory)) {
                    stream.filter(path -> !path.equals(directory)).limit(properties.maxListEntries())
                            .forEach(path -> addEntry(result, path));
                }
            }
            return result;
        } catch (IOException exception) {
            throw ioFailure("列出工作区目录失败", displayPath, exception);
        }
    }

    private void addEntry(List<Map<String, Object>> result, Path path) {
        if (Files.isSymbolicLink(path) || !isAllowedPath(path)) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", root().relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"));
        entry.put("type", Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "directory" : "file");
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            try {
                entry.put("size", Files.size(path));
            } catch (IOException ignored) {
                entry.put("size", 0);
            }
        }
        result.add(entry);
    }

    public List<Map<String, Object>> search(String query, Path directory, String displayPath,
                                            boolean caseSensitive, int maxResults) {
        if (query == null || query.isBlank()) {
            throw invalidInput("workspace.search", "query 不能为空");
        }
        String needle = caseSensitive ? query : query.toLowerCase(java.util.Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isAllowedPath)
                    .limit(properties.maxSearchFiles())
                    .forEach(path -> searchFile(path, needle, caseSensitive, maxResults, result));
        } catch (IOException exception) {
            throw ioFailure("搜索工作区失败", displayPath, exception);
        }
        return result;
    }

    private void searchFile(Path path, String needle, boolean caseSensitive, int maxResults,
                            List<Map<String, Object>> result) {
        if (result.size() >= maxResults) return;
        try {
            if (Files.size(path) > properties.maxReadBytes()) return;
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size() && result.size() < maxResults; index++) {
                String line = lines.get(index);
                String haystack = caseSensitive ? line : line.toLowerCase(java.util.Locale.ROOT);
                if (haystack.contains(needle)) {
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("path", root().relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"));
                    match.put("line", index + 1);
                    String text = sanitizer.sanitize(line);
                    if (text != null && text.length() > properties.maxSearchMatchChars()) {
                        match.put("text", truncateText(text, properties.maxSearchMatchChars()));
                        match.put("textTruncated", true);
                    } else {
                        match.put("text", text);
                    }
                    result.add(match);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 二进制文件或被并发删除的文件不应阻断其他文件的搜索结果。
        }
    }

    /**
     * 构建有界的搜索结果。搜索命中本身受 {@code maxResults} 限制，但单行内容和命中数量
     * 仍可能在一次调用中产生远超模型上下文的 JSON；这里按完整条目裁剪，绝不直接截断 JSON
     * 字符串，以保证模型始终收到可解析的结果。
     */
    public String boundedSearchJson(String query, String displayPath,
                                    List<Map<String, Object>> matches,
                                    boolean searchTruncated) {
        List<Map<String, Object>> boundedMatches = matches == null
                ? new ArrayList<>() : new ArrayList<>(matches);
        String boundedQuery = query == null ? "" : query;
        String boundedPath = displayPath == null || displayPath.isBlank() ? "." : displayPath;
        boolean truncated = searchTruncated;
        String serialized = serializeSearchResult(boundedQuery, boundedPath, boundedMatches,
                truncated, matches == null ? 0 : matches.size());

        // 只移除完整的尾部命中，避免 JSON 结构被破坏；一旦移除则明确告诉 Agent。
        while (serialized.length() > properties.maxToolOutputChars() && !boundedMatches.isEmpty()) {
            boundedMatches.remove(boundedMatches.size() - 1);
            truncated = true;
            serialized = serializeSearchResult(boundedQuery, boundedPath, boundedMatches,
                    true, matches == null ? 0 : matches.size());
        }

        // 查询路径通常很短，但输入来自模型，仍需防止异常长的元数据挤占输出预算。
        if (serialized.length() > properties.maxToolOutputChars()) {
            truncated = true;
            boundedQuery = truncateText(boundedQuery, 128);
            boundedPath = truncateText(boundedPath, 128);
            serialized = serializeSearchResult(boundedQuery, boundedPath, boundedMatches,
                    true, matches == null ? 0 : matches.size());
        }
        if (serialized.length() > properties.maxToolOutputChars()) {
            boundedQuery = "";
            boundedPath = ".";
            serialized = serializeSearchResult(boundedQuery, boundedPath, boundedMatches,
                    true, matches == null ? 0 : matches.size());
        }

        // maxToolOutputChars 经过配置归一化后至少足以容纳此最小结构；保留最后一道兜底，
        // 以防未来增加字段或外部构造了异常小的配置值。
        if (serialized.length() > properties.maxToolOutputChars()) {
            serialized = serializeSearchResult("", ".", List.of(), true,
                    matches == null ? 0 : matches.size());
        }
        return serialized;
    }

    private String serializeSearchResult(String query, String displayPath,
                                         List<Map<String, Object>> matches,
                                         boolean truncated, int observedMatches) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("path", displayPath);
        result.put("matches", matches);
        result.put("truncated", truncated);
        if (truncated) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("returned", matches.size());
            summary.put("observed", observedMatches);
            summary.put("maxOutputChars", properties.maxToolOutputChars());
            result.put("summary", summary);
        }
        return json(result);
    }

    private String truncateText(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value;
        int end = Math.max(0, Math.min(maxChars, value.length()));
        // 不切开一个 UTF-16 代理对，避免把单条命中的文本变成非法字符。
        if (end > 0 && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    public String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化工作区工具结果", exception);
        }
    }

    /**
     * Git 不是所有工作区都具备。对 Agent 来说，这类环境能力缺失应当是可解释的结果，
     * 而不是让整轮任务直接失败；调用方仍可根据 {@code available=false} 选择普通文件工具继续工作。
     */
    public String gitUnavailable(String toolName, BusinessException exception) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("available", false);
        result.put("recoverable", true);
        // Git 不可用不能作为 workspace.git.diff 的有效修改核验依据。
        result.put("verificationEligible", false);
        result.put("tool", toolName);
        result.put("code", exception.getCode());
        result.put("message", sanitizer.sanitize(exception.getMessage()));
        result.put("suggestion", "当前工作区仍可使用 workspace.list、workspace.search 或 workspace.read 继续检查文件");
        return json(result);
    }

    /**
     * 只读工作区调用的路径或文件内容不符合预期时，给 Agent 一个可继续推理的结果。
     * 写入、审批和基础设施错误不走这条路径，仍然抛出原始业务异常。
     */
    public String recoverableReadFailure(String toolName, BusinessException exception,
                                         String suggestion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("available", true);
        result.put("recoverable", true);
        result.put("verificationEligible", false);
        result.put("tool", toolName);
        result.put("code", exception.getCode());
        result.put("message", sanitizer.sanitize(exception.getMessage()));
        result.put("suggestion", sanitizer.sanitize(suggestion));
        return json(result);
    }

    public boolean isRecoverableReadFailure(BusinessException exception) {
        return exception != null && RECOVERABLE_READ_FAILURES.contains(exception.getCode());
    }

    public String sanitize(String value) {
        return sanitizer.sanitize(value);
    }

    public String relative(Path path) {
        return root().relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private void ensureRootDirectory(Path root) {
        try {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(root);
            }
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw invalidPath(root.toString());
            }
        } catch (IOException exception) {
            throw ioFailure("无法准备工作区根目录", root.toString(), exception);
        }
    }

    private void createDirectoriesInsideRoot(Path directory) throws IOException {
        Files.createDirectories(directory);
        ensureWithinRoot(directory.toRealPath());
        if (Files.isSymbolicLink(directory)) {
            throw invalidPath(directory.toString());
        }
    }

    private void ensureWithinRoot(Path realPath) {
        try {
            Path realRoot = root().toRealPath();
            if (!realPath.startsWith(realRoot)) {
                throw invalidPath(realPath.toString());
            }
        } catch (IOException exception) {
            throw ioFailure("无法校验工作区路径", realPath.toString(), exception);
        }
    }

    private Path realPath(Path path, String displayPath) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw ioFailure("无法解析工作区路径", displayPath, exception);
        }
    }

    private void ensureRegularFile(Path path, String displayPath) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_NOT_FILE",
                    "工作区路径不是普通文件: " + displayPath);
        }
    }

    private boolean isAllowedPath(Path path) {
        if (properties.allowHiddenFiles()) return true;
        for (Path part : root().relativize(path)) {
            if (part.toString().startsWith(".")) return false;
        }
        return true;
    }

    private void checkHiddenSegments(Path path) {
        if (properties.allowHiddenFiles()) return;
        for (Path part : root().relativize(path)) {
            String value = part.toString();
            if (value.startsWith(".")) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "WORKSPACE_HIDDEN_PATH_DENIED",
                        "默认不允许访问隐藏路径");
            }
        }
    }

    private void checkNoSymlinkSegments(Path path, String displayPath) {
        Path root = root();
        Path current = root;
        for (Path part : root.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw invalidPath(displayPath);
            }
        }
    }

    private String decodeUtf8(byte[] bytes, String displayPath) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WORKSPACE_BINARY_FILE",
                    "工作区文件不是有效 UTF-8 文本: " + displayPath);
        }
    }

    private BusinessException invalidPath(String value) {
        return new BusinessException(HttpStatus.FORBIDDEN, "WORKSPACE_PATH_DENIED",
                "路径必须位于工作区内: " + value);
    }

    private BusinessException invalidInput(String toolName, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "WORKSPACE_INPUT_INVALID",
                toolName + " 输入无效: " + message);
    }

    private BusinessException ioFailure(String message, String path, IOException exception) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "WORKSPACE_IO_FAILED",
                message + ": " + path);
    }
}
