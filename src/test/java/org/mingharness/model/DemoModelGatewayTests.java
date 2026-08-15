package org.mingharness.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoModelGatewayTests {

    private final DemoModelGateway gateway = new DemoModelGateway();

    @Test
    void shouldReturnTraceableModelResponse() {
        ModelResponse response = gateway.complete(new ModelRequest("分析退款申请", "demo-model", "prompt-v2"));

        assertTrue(response.content().contains("分析退款申请"));
        assertEquals("demo-model", response.model());
        assertEquals("prompt-v2", response.promptVersion());
        assertTrue(response.inputTokens() > 0);
        assertTrue(response.outputTokens() > 0);
    }

    @Test
    void shouldDemonstrateAnAgentToolRoundTripWhenToolsAreAvailable() {
        ModelToolDefinition echo = new ModelToolDefinition(
                "demo.echo", "返回输入内容", Map.of("type", "string"));
        ModelRequest firstRequest = new ModelRequest(
                "请检查这段输入", "demo-model", "prompt-agent", List.of(echo),
                List.of(ModelMessage.system("你是代码 Agent"), ModelMessage.user("请检查这段输入")));

        ModelResponse first = gateway.complete(firstRequest);

        assertEquals("demo.echo", first.toolCalls().get(0).name());
        assertTrue(first.content().contains("正在执行工具"));

        ModelRequest secondRequest = new ModelRequest(
                firstRequest.input(), firstRequest.model(), firstRequest.promptVersion(), List.of(echo),
                List.of(
                        ModelMessage.system("你是代码 Agent"),
                        ModelMessage.user("请检查这段输入"),
                        ModelMessage.assistant(first.content(), first.toolCalls()),
                        ModelMessage.tool(first.toolCalls().get(0).id(), "工具已完成: 请检查这段输入")
                ));
        ModelResponse second = gateway.complete(secondRequest);

        assertTrue(second.toolCalls().isEmpty());
        assertTrue(second.content().contains("工具已完成: 请检查这段输入"));
    }

    @Test
    void shouldKeepEducationAgentOutputLearnerFriendly() {
        ModelToolDefinition echo = new ModelToolDefinition(
                "demo.echo", "返回输入内容", Map.of("type", "string"));
        ModelRequest initial = new ModelRequest(
                "教育任务约束（必须遵守）：学科=数学；目标知识点=函数定义域；检索策略=FULL",
                "demo-model", "prompt-agent", List.of(echo), List.of(
                ModelMessage.system("你是一个面向课程约束与学习者状态的教育知识库 Agent"),
                ModelMessage.user("请开始练习")));

        ModelResponse first = gateway.complete(initial);
        ModelResponse finalResponse = gateway.complete(new ModelRequest(
                initial.input(), initial.model(), initial.promptVersion(), initial.tools(), List.of(
                ModelMessage.system("你是一个面向课程约束与学习者状态的教育知识库 Agent"),
                ModelMessage.user("请开始练习"),
                ModelMessage.assistant(first.content(), first.toolCalls()),
                ModelMessage.tool(first.toolCalls().get(0).id(), initial.input()))));

        assertTrue(finalResponse.content().contains("函数定义域"));
        assertTrue(finalResponse.content().contains("答案或解题过程"));
        assertTrue(!finalResponse.content().contains("教育任务约束"));
        assertTrue(!finalResponse.content().contains("检索策略"));
    }

    @Test
    void shouldInspectARepresentativeSourceFileInWorkspaceDemoFlow() {
        ModelToolDefinition list = new ModelToolDefinition(
                "workspace.list", "列出工作区目录结构", Map.of("type", "object"));
        ModelToolDefinition search = new ModelToolDefinition(
                "workspace.search", "搜索工作区代码", Map.of("type", "object"));
        ModelToolDefinition read = new ModelToolDefinition(
                "workspace.read", "读取工作区文件", Map.of("type", "object"));
        List<ModelToolDefinition> tools = List.of(list, search, read);
        ModelRequest initial = new ModelRequest(
                "请理解项目入口", "demo-model", "prompt-agent", tools,
                List.of(ModelMessage.system("你是代码 Agent"), ModelMessage.user("请理解项目入口")));

        ModelResponse first = gateway.complete(initial);
        assertEquals("workspace.list", first.toolCalls().get(0).name());
        assertTrue(first.toolCalls().get(0).arguments().contains("\"recursive\":false"));

        String rootListing = "{\"path\":\".\",\"entries\":["
                + "{\"path\":\"src\",\"type\":\"directory\"},"
                + "{\"path\":\"README.md\",\"type\":\"file\"}]}";
        ModelRequest sourceListingRequest = withToolResult(initial, first, rootListing);
        ModelResponse second = gateway.complete(sourceListingRequest);
        assertEquals("workspace.list", second.toolCalls().get(0).name());
        assertTrue(second.toolCalls().get(0).arguments().contains("\"path\":\"src\""));
        assertTrue(second.toolCalls().get(0).arguments().contains("\"recursive\":true"));

        String sourceListing = "{\"path\":\"src\",\"entries\":["
                + "{\"path\":\"src/main/App.java\",\"type\":\"file\"},"
                + "{\"path\":\"src/main\",\"type\":\"directory\"}]}";
        ModelRequest searchRequest = withToolResult(sourceListingRequest, second, sourceListing);
        ModelResponse third = gateway.complete(searchRequest);
        assertEquals("workspace.search", third.toolCalls().get(0).name());
        assertTrue(third.toolCalls().get(0).arguments().contains("\"query\":\"App\""));
        assertTrue(third.toolCalls().get(0).arguments().contains("\"path\":\"src/main\""));

        String searchResult = "{\"query\":\"App\",\"path\":\"src/main\",\"matches\":["
                + "{\"path\":\"src/main/App.java\",\"line\":1,\"text\":\"public class App {}\"}]}";
        ModelRequest readRequest = withToolResult(searchRequest, third, searchResult);
        ModelResponse fourth = gateway.complete(readRequest);
        assertEquals("workspace.read", fourth.toolCalls().get(0).name());
        assertTrue(fourth.toolCalls().get(0).arguments().contains("\"path\":\"src/main/App.java\""));
        assertTrue(fourth.toolCalls().get(0).arguments().contains("\"endLine\":120"));

        String readResult = "{\"path\":\"src/main/App.java\",\"totalLines\":3,"
                + "\"sha256\":\"abc123\",\"content\":\"public class App {}\\n"
                + "static void run() {}\"}";
        ModelResponse finalResponse = gateway.complete(withToolResult(readRequest, fourth, readResult));
        assertTrue(finalResponse.toolCalls().isEmpty());
        assertTrue(finalResponse.content().contains("src/main/App.java"));
        assertTrue(finalResponse.content().contains("static void run()"));
        assertTrue(finalResponse.content().contains("abc123"));
    }

    @Test
    void shouldFallBackToReadingFilesWhenGitIsUnavailable() {
        ModelToolDefinition list = new ModelToolDefinition(
                "workspace.list", "列出工作区目录结构", Map.of("type", "object"));
        ModelToolDefinition read = new ModelToolDefinition(
                "workspace.read", "读取工作区文件", Map.of("type", "object"));
        ModelToolDefinition gitStatus = new ModelToolDefinition(
                "workspace.git.status", "查看 Git 状态", Map.of("type", "object"));
        ModelToolCall listCall = new ModelToolCall("list-call", "workspace.list", "{}");
        ModelToolCall gitCall = new ModelToolCall("git-call", "workspace.git.status", "{}");
        List<ModelMessage> messages = new java.util.ArrayList<>(List.of(
                ModelMessage.system("你是代码 Agent"),
                ModelMessage.user("请检查项目"),
                ModelMessage.assistant("先浏览工作区", List.of(listCall)),
                ModelMessage.tool(listCall.id(), "{\"path\":\".\",\"entries\":["
                        + "{\"path\":\"src/App.java\",\"type\":\"file\"}] }"),
                ModelMessage.assistant("尝试查看 Git", List.of(gitCall)),
                ModelMessage.tool(gitCall.id(), "{\"ok\":false,\"available\":false,"
                        + "\"recoverable\":true,\"verificationEligible\":false}")));

        ModelResponse response = gateway.complete(new ModelRequest(
                "请检查项目", "demo-model", "prompt-agent", List.of(list, read, gitStatus), messages));

        assertEquals("workspace.read", response.toolCalls().get(0).name());
        assertTrue(response.content().contains("Git 审阅不可用"));
        assertTrue(response.toolCalls().get(0).arguments().contains("src/App.java"));
    }

    @Test
    void shouldRebrowseWorkspaceWhenAReadToolReturnsRecoverableError() {
        ModelToolDefinition list = new ModelToolDefinition(
                "workspace.list", "列出工作区目录结构", Map.of("type", "object"));
        ModelToolDefinition read = new ModelToolDefinition(
                "workspace.read", "读取工作区文件", Map.of("type", "object"));
        ModelToolCall readCall = new ModelToolCall("stale-read", "workspace.read",
                "{\"path\":\"old/App.java\"}");
        ModelResponse response = gateway.complete(new ModelRequest(
                "请检查项目", "demo-model", "prompt-agent", List.of(list, read), List.of(
                ModelMessage.system("你是代码 Agent"),
                ModelMessage.user("请检查项目"),
                ModelMessage.assistant("读取旧路径", List.of(readCall)),
                ModelMessage.tool(readCall.id(), "{\"ok\":false,\"available\":true,"
                        + "\"recoverable\":true,\"code\":\"WORKSPACE_PATH_NOT_FOUND\"}"))));

        assertEquals("workspace.list", response.toolCalls().get(0).name());
        assertTrue(response.content().contains("重新浏览工作区"));
        assertTrue(response.toolCalls().get(0).arguments().contains("\"path\":\".\""));
    }

    private ModelRequest withToolResult(ModelRequest previousRequest,
                                        ModelResponse response, String toolResult) {
        List<ModelMessage> messages = new java.util.ArrayList<>(previousRequest.messages());
        messages.add(ModelMessage.assistant(response.content(), response.toolCalls()));
        messages.add(ModelMessage.tool(response.toolCalls().get(0).id(), toolResult));
        return new ModelRequest(previousRequest.input(), previousRequest.model(),
                previousRequest.promptVersion(), previousRequest.tools(), messages);
    }
}
