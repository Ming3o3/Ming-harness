package org.mingharness.model;

import org.junit.jupiter.api.Test;

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
}
