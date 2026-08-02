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
}
