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
    void shouldInspectARepresentativeSourceFileInWorkspaceDemoFlow() {
        ModelToolDefinition list = new ModelToolDefinition(
                "workspace.list", "列出工作区目录结构", Map.of("type", "object"));
        ModelToolDefinition read = new ModelToolDefinition(
                "workspace.read", "读取工作区文件", Map.of("type", "object"));
        List<ModelToolDefinition> tools = List.of(list, read);
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
        ModelRequest readRequest = withToolResult(sourceListingRequest, second, sourceListing);
        ModelResponse third = gateway.complete(readRequest);
        assertEquals("workspace.read", third.toolCalls().get(0).name());
        assertTrue(third.toolCalls().get(0).arguments().contains("\"path\":\"src/main/App.java\""));
        assertTrue(third.toolCalls().get(0).arguments().contains("\"endLine\":120"));

        String readResult = "{\"path\":\"src/main/App.java\",\"totalLines\":3,"
                + "\"sha256\":\"abc123\",\"content\":\"public class App {}\\n"
                + "static void run() {}\"}";
        ModelResponse finalResponse = gateway.complete(withToolResult(readRequest, third, readResult));
        assertTrue(finalResponse.toolCalls().isEmpty());
        assertTrue(finalResponse.content().contains("src/main/App.java"));
        assertTrue(finalResponse.content().contains("static void run()"));
        assertTrue(finalResponse.content().contains("abc123"));
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
