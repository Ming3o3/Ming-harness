package org.mingharness.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class DemoModelGateway implements ModelGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int READ_PREVIEW_LINES = 120;
    private static final int FINAL_PREVIEW_CHARS = 1_200;

    @Override
    public ModelResponse complete(ModelRequest request) {
        String input = request.input() == null ? "" : request.input().trim();
        boolean educationRequest = isEducationRequest(request);
        if (!request.tools().isEmpty()) {
            return completeAgentDemo(request, input);
        }
        if (educationRequest) {
            return response(request, input, educationReadyResponse(input), List.of());
        }
        String content = input.isBlank()
                ? "演示模型已收到任务，但没有可处理的输入。"
                : "演示模型分析结果：" + input;
        return response(request, input, content, List.of());
    }

    /**
     * 默认演示模型也跑一遍确定性的 Agent 代码理解闭环，便于本地未配置外部模型时验证工具、步骤和审计体验。
     * 真实模型接入后仍由 OpenAI 兼容网关负责自然语言决策；演示路径最多浏览一层源码目录，
     * 通过搜索定位并读取一个代表性文本文件，或在工作区工具不可用时退回安全的回显工具。
     */
    private ModelResponse completeAgentDemo(ModelRequest request, String input) {
        boolean educationRequest = isEducationRequest(request);
        Optional<ModelMessage> latestToolMessage = latestToolMessage(request.messages());
        if (latestToolMessage.isEmpty()) {
            Optional<ModelToolDefinition> workspaceList = request.tools().stream()
                    .filter(tool -> "workspace.list".equals(tool.name()))
                    .findFirst();
            if (workspaceList.isPresent()) {
                return response(request, input, "演示 Agent 正在检查工作区结构…",
                        List.of(new ModelToolCall("demo-workspace-list", "workspace.list",
                                "{\"path\":\".\",\"recursive\":false}")));
            }
            Optional<ModelToolDefinition> demoEcho = request.tools().stream()
                    .filter(tool -> "demo.echo".equals(tool.name()))
                    .findFirst();
            if (demoEcho.isPresent()) {
                return response(request, input, "演示 Agent 正在执行工具…",
                        List.of(new ModelToolCall("demo-echo", "demo.echo", jsonString(input))));
            }
        }

        String toolResult = latestToolMessage.map(ModelMessage::content).orElse("");
        String latestToolName = latestToolName(request.messages());
        if ("workspace.list".equals(latestToolName)) {
            Optional<ModelToolDefinition> workspaceRead = request.tools().stream()
                    .filter(tool -> "workspace.read".equals(tool.name()))
                    .findFirst();
            if (workspaceRead.isPresent()) {
                Optional<String> nextDirectory = nextSourceDirectory(toolResult);
                if (nextDirectory.isPresent()) {
                    return response(request, input, "演示 Agent 正在深入检查源代码目录…",
                            List.of(new ModelToolCall("demo-workspace-list-source", "workspace.list",
                                    jsonObject(Map.of("path", nextDirectory.get(), "recursive", true)))));
                }
                Optional<String> candidate = firstReadableFile(toolResult);
                if (candidate.isPresent()) {
                    Optional<ModelToolDefinition> workspaceSearch = request.tools().stream()
                            .filter(tool -> "workspace.search".equals(tool.name()))
                            .findFirst();
                    if (workspaceSearch.isPresent()) {
                        return response(request, input, "演示 Agent 正在搜索相关代码位置…",
                                List.of(new ModelToolCall("demo-workspace-search", "workspace.search",
                                        jsonObject(Map.of("query", searchQuery(candidate.get()),
                                                "path", parentDirectory(candidate.get()), "maxResults", 8)))));
                    }
                    return response(request, input, "演示 Agent 正在读取关键文件…",
                            List.of(new ModelToolCall("demo-workspace-read", "workspace.read",
                                    jsonObject(Map.of("path", candidate.get(), "startLine", 1,
                                            "endLine", READ_PREVIEW_LINES)))));
                }
            }
        }

        if ("workspace.search".equals(latestToolName)) {
            Optional<ModelToolDefinition> workspaceRead = request.tools().stream()
                    .filter(tool -> "workspace.read".equals(tool.name()))
                    .findFirst();
            if (workspaceRead.isPresent()) {
                Optional<String> candidate = firstSearchMatch(toolResult)
                        .or(() -> firstReadableFile(latestToolResult(request.messages(), "workspace.list")));
                if (candidate.isPresent()) {
                    return response(request, input, "演示 Agent 正在读取搜索到的关键文件…",
                            List.of(new ModelToolCall("demo-workspace-read", "workspace.read",
                                    jsonObject(Map.of("path", candidate.get(), "startLine", 1,
                                            "endLine", READ_PREVIEW_LINES)))));
                }
            }
        }

        // Git 只是辅助审阅能力，非 Git 工作区仍然可以继续浏览普通文件。
        // 将结构化不可用结果转成下一步工具调用，演示模型与真实 Agent 的降级行为保持一致。
        if (("workspace.git.status".equals(latestToolName)
                || "workspace.git.diff".equals(latestToolName))
                && isRecoverableToolResult(toolResult)) {
            Optional<ModelToolDefinition> workspaceRead = request.tools().stream()
                    .filter(tool -> "workspace.read".equals(tool.name()))
                    .findFirst();
            if (workspaceRead.isPresent()) {
                Optional<String> candidate = firstReadableFile(latestToolResult(request.messages(), "workspace.list"));
                if (candidate.isPresent()) {
                    return response(request, input, "Git 审阅不可用，演示 Agent 改为读取关键文件…",
                            List.of(new ModelToolCall("demo-workspace-read-after-git-fallback", "workspace.read",
                                    jsonObject(Map.of("path", candidate.get(), "startLine", 1,
                                            "endLine", READ_PREVIEW_LINES)))));
                }
            }
            Optional<ModelToolDefinition> workspaceList = request.tools().stream()
                    .filter(tool -> "workspace.list".equals(tool.name()))
                    .findFirst();
            if (workspaceList.isPresent()) {
                return response(request, input, "Git 审阅不可用，演示 Agent 先重新浏览工作区…",
                        List.of(new ModelToolCall("demo-workspace-list-after-git-fallback", "workspace.list",
                                "{\"path\":\".\",\"recursive\":false}")));
            }
        }

        // 路径可能在模型规划后被用户或其他进程改变；从根目录重新定位比直接终止更接近桌面 Agent 体验。
        if (isRecoverableToolResult(toolResult)) {
            Optional<ModelToolDefinition> workspaceList = request.tools().stream()
                    .filter(tool -> "workspace.list".equals(tool.name()))
                    .findFirst();
            if (workspaceList.isPresent()) {
                return response(request, input, "上一步文件定位未成功，演示 Agent 正在重新浏览工作区…",
                        List.of(new ModelToolCall("demo-workspace-list-after-read-fallback", "workspace.list",
                                "{\"path\":\".\",\"recursive\":false}")));
            }
        }

        if ("workspace.read".equals(latestToolName) && !educationRequest) {
            String readSummary = summarizeReadResult(toolResult);
            if (!readSummary.isBlank()) {
                return response(request, input, readSummary, List.of());
            }
        }

        // 本地演示工具可能只是把教育输入原样回显；学生不应看到课程约束、课程实例
        // 或检索策略等内部上下文。真实模型接入时仍由教育系统提示词约束输出，演示路径
        // 也必须保持同样的用户边界。
        if (educationRequest) {
            return response(request, input, educationReadyResponse(input), List.of());
        }

        String clippedResult = toolResult.length() > 800
                ? toolResult.substring(0, 800) + "…" : toolResult;
        String content = clippedResult.isBlank()
                ? "演示 Agent 已完成任务，但没有可展示的工具结果。"
                : "演示 Agent 已完成任务。\n\n工具结果：\n" + clippedResult;
        return response(request, input, content, List.of());
    }

    private boolean isEducationRequest(ModelRequest request) {
        if (request == null) return false;
        if (containsEducationInternals(request.input())) return true;
        return request.messages() != null && request.messages().stream()
                .filter(message -> message != null && ("system".equals(message.role())
                        || "user".equals(message.role())))
                .map(ModelMessage::content)
                .anyMatch(this::containsEducationInternals);
    }

    private boolean containsEducationInternals(String value) {
        if (value == null || value.isBlank()) return false;
        return value.contains("教育任务约束")
                || value.contains("课程实例=")
                || value.contains("检索策略=")
                || value.contains("教育知识库 Agent")
                || value.contains("学习者状态=");
    }

    private String educationReadyResponse(String input) {
        String concept = extractEducationField(input, "目标知识点");
        if (concept.isBlank()) concept = extractEducationField(input, "知识点");
        String focus = concept.isBlank() ? "当前课程内容" : "「" + concept + "」";
        return "本次学习已准备好，我们来完成" + focus + "的练习。\n\n"
                + "请直接写下你的答案或解题过程；如果暂时不会，也可以先写出你的思路。"
                + "我会根据你的作答给出提示、反馈，并更新学习进度。";
    }

    private String extractEducationField(String input, String label) {
        if (input == null || input.isBlank()) return "";
        String marker = label + "=";
        int start = input.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = input.indexOf('；', start);
        if (end < 0) end = input.indexOf('\n', start);
        if (end < 0) end = input.length();
        return input.substring(start, end).trim();
    }

    private boolean isRecoverableToolResult(String rawResult) {
        JsonNode result = parseJson(rawResult);
        return result != null && result.path("recoverable").asBoolean(false);
    }

    private Optional<ModelMessage> latestToolMessage(List<ModelMessage> messages) {
        if (messages == null) return Optional.empty();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ModelMessage message = messages.get(index);
            if (message != null && "tool".equals(message.role())) return Optional.of(message);
        }
        return Optional.empty();
    }

    private String latestToolName(List<ModelMessage> messages) {
        Optional<ModelMessage> toolMessage = latestToolMessage(messages);
        if (toolMessage.isEmpty() || messages == null) return "";
        String toolCallId = toolMessage.get().toolCallId();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ModelMessage message = messages.get(index);
            if (message == null || !"assistant".equals(message.role())) continue;
            for (int callIndex = message.toolCalls().size() - 1; callIndex >= 0; callIndex--) {
                ModelToolCall call = message.toolCalls().get(callIndex);
                if (toolCallId == null || toolCallId.isBlank() || toolCallId.equals(call.id())) {
                    return call.name();
                }
            }
        }
        return "";
    }

    private String latestToolResult(List<ModelMessage> messages, String toolName) {
        if (messages == null || toolName == null || toolName.isBlank()) return "";
        for (int index = messages.size() - 1; index >= 0; index--) {
            ModelMessage message = messages.get(index);
            if (message == null || !"tool".equals(message.role())) continue;
            String toolCallId = message.toolCallId();
            for (int previous = index - 1; previous >= 0; previous--) {
                ModelMessage assistantMessage = messages.get(previous);
                if (assistantMessage == null || !"assistant".equals(assistantMessage.role())) continue;
                for (int callIndex = assistantMessage.toolCalls().size() - 1; callIndex >= 0; callIndex--) {
                    ModelToolCall call = assistantMessage.toolCalls().get(callIndex);
                    if ((toolCallId == null || toolCallId.isBlank() || toolCallId.equals(call.id()))
                            && toolName.equals(call.name())) {
                        return message.content();
                    }
                }
            }
        }
        return "";
    }

    /** 根目录下存在典型源码目录时，先深入一层，避免递归扫描依赖目录吞掉安全上限。 */
    private Optional<String> nextSourceDirectory(String rawResult) {
        JsonNode root = parseJson(rawResult);
        if (root == null || !".".equals(root.path("path").asText("."))) return Optional.empty();
        JsonNode entries = root.path("entries");
        if (!entries.isArray()) return Optional.empty();
        return streamEntries(entries).stream()
                .filter(entry -> "directory".equals(entry.path("type").asText()))
                .filter(entry -> !entry.path("path").asText().contains("/"))
                .filter(entry -> sourceDirectoryRank(entry.path("path").asText()) < Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt(entry -> sourceDirectoryRank(entry.path("path").asText())))
                .map(entry -> entry.path("path").asText())
                .findFirst();
    }

    private Optional<String> firstReadableFile(String rawResult) {
        JsonNode root = parseJson(rawResult);
        if (root == null) return Optional.empty();
        JsonNode entries = root.path("entries");
        if (!entries.isArray()) return Optional.empty();
        return streamEntries(entries).stream()
                .filter(entry -> "file".equals(entry.path("type").asText()))
                .map(entry -> entry.path("path").asText())
                .filter(path -> !path.isBlank() && !isIgnoredPath(path))
                .filter(this::looksReadable)
                .sorted(Comparator.comparingInt(this::fileRank).thenComparing(String::length))
                .findFirst();
    }

    private Optional<String> firstSearchMatch(String rawResult) {
        JsonNode root = parseJson(rawResult);
        if (root == null || !root.path("matches").isArray()) return Optional.empty();
        return streamEntries(root.path("matches")).stream()
                .map(match -> match.path("path").asText())
                .filter(path -> !path.isBlank() && !isIgnoredPath(path) && looksReadable(path))
                .findFirst();
    }

    private String searchQuery(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int extensionIndex = fileName.lastIndexOf('.');
        String stem = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return stem.isBlank() ? "TODO" : stem;
    }

    private String parentDirectory(String path) {
        int separator = path.lastIndexOf('/');
        return separator > 0 ? path.substring(0, separator) : ".";
    }

    private List<JsonNode> streamEntries(JsonNode entries) {
        List<JsonNode> result = new ArrayList<>();
        entries.forEach(result::add);
        return result;
    }

    private JsonNode parseJson(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readTree(rawValue);
        } catch (JacksonException ignored) {
            return null;
        }
    }

    private int sourceDirectoryRank(String path) {
        return switch (path.toLowerCase(Locale.ROOT)) {
            case "src" -> 0;
            case "app" -> 1;
            case "lib" -> 2;
            case "frontend" -> 3;
            case "backend" -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    private int fileRank(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".ts")
                || lower.endsWith(".tsx") || lower.endsWith(".vue") || lower.endsWith(".py")
                || lower.endsWith(".go") || lower.endsWith(".rs")) return 0;
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".cs")
                || lower.endsWith(".cpp") || lower.endsWith(".c")) return 1;
        if (lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yaml")
                || lower.endsWith(".yml") || lower.endsWith(".properties")) return 2;
        if (lower.endsWith(".md") || lower.endsWith(".txt")) return 3;
        return 4;
    }

    private boolean looksReadable(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains(".") && fileRank(path) < 4;
    }

    private boolean isIgnoredPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith(".") || lower.contains("/.")
                || lower.contains("/node_modules/") || lower.contains("/target/")
                || lower.contains("/dist/") || lower.contains("/build/")
                || lower.contains("/.git/");
    }

    private String summarizeReadResult(String rawResult) {
        JsonNode root = parseJson(rawResult);
        if (root == null || !root.path("content").isTextual()) return "";
        String path = root.path("path").asText("工作区文件");
        String content = root.path("content").asText("");
        String preview = content.length() > FINAL_PREVIEW_CHARS
                ? content.substring(0, FINAL_PREVIEW_CHARS) + "\n…" : content;
        String hash = root.path("sha256").asText("");
        String lineInfo = root.path("totalLines").isNumber()
                ? "，共 " + root.path("totalLines").asInt() + " 行" : "";
        return "演示 Agent 已完成项目理解。\n\n已读取：" + path + lineInfo
                + (hash.isBlank() ? "" : "，sha256=" + hash)
                + "\n\n关键内容片段：\n" + preview;
    }

    private String jsonObject(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法创建演示工具参数", exception);
        }
    }

    private ModelResponse response(ModelRequest request, String input, String content,
                                   List<ModelToolCall> toolCalls) {
        int inputTokens = estimateTokens(input);
        int outputTokens = estimateTokens(content);
        return new ModelResponse(
                content,
                request.model() == null || request.model().isBlank() ? "demo-model" : request.model(),
                request.promptVersion() == null || request.promptVersion().isBlank() ? "prompt-v1" : request.promptVersion(),
                inputTokens,
                outputTokens,
                BigDecimal.valueOf(inputTokens + outputTokens).multiply(BigDecimal.valueOf(0.000001)),
                toolCalls
        );
    }

    private String jsonString(String value) {
        String escaped = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }

    private int estimateTokens(String value) {
        return Math.max(1, value.length() / 4);
    }
}
