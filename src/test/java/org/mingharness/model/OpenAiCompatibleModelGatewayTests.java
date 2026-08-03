package org.mingharness.model;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用本地 HTTP 服务验证 OpenAI 兼容供应商的成功、失败和回退协议。 */
class OpenAiCompatibleModelGatewayTests {

    private final List<HttpServer> servers = new ArrayList<>();
    private final SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void shouldParseUsageCalculateCostAndSanitizeModelContent() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200, """
                {"model":"provider-model","choices":[{"message":{"content":"api_key=do-not-return"}}],
                "usage":{"prompt_tokens":120,"completion_tokens":80}}
                """));
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null));

        ModelResponse response = gateway.complete(new ModelRequest("分析订单", "", "prompt-v2"));

        assertEquals("provider-model", response.model());
        assertEquals("prompt-v2", response.promptVersion());
        assertEquals(120, response.inputTokens());
        assertEquals(80, response.outputTokens());
        assertEquals(0, response.cost().compareTo(new BigDecimal("0.14")));
        assertFalse(response.content().contains("do-not-return"));
        assertTrue(response.content().contains(SensitiveDataSanitizer.REDACTION_MARKER));
    }

    @Test
    void shouldRetryRetryableServerFailureThenSucceed() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 503, "{\"error\":\"temporary\"}");
                return;
            }
            respond(exchange, 200, success("重试成功"));
        });
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 2, null, null));

        ModelResponse response = gateway.complete(new ModelRequest("分析订单", "model-a", "prompt-v1"));

        assertEquals(2, calls.get());
        assertEquals("重试成功", response.content());
    }

    @Test
    void shouldRouteToFallbackAfterPrimaryTemporaryFailure() throws IOException {
        AtomicInteger primaryCalls = new AtomicInteger();
        HttpServer primary = server(exchange -> {
            primaryCalls.incrementAndGet();
            respond(exchange, 503, "{\"error\":\"temporary\"}");
        });
        HttpServer fallback = server(exchange -> respond(exchange, 200, success("备用供应商成功")));
        OpenAiCompatibleModelGateway gateway = gateway(config(url(primary), 1, url(fallback), "fallback-model"));

        ModelResponse response = gateway.complete(new ModelRequest("分析订单", "", "prompt-v1"));

        assertEquals(1, primaryCalls.get());
        assertEquals("备用供应商成功", response.content());
        assertEquals("provider-model", response.model());
    }

    @Test
    void shouldOpenCircuitAfterConfiguredConsecutiveFailures() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "{\"error\":\"temporary\"}");
        });
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null, 1));

        ModelGatewayException first = assertThrows(ModelGatewayException.class,
                () -> gateway.complete(new ModelRequest("分析订单", "", "prompt-v1")));
        ModelGatewayException second = assertThrows(ModelGatewayException.class,
                () -> gateway.complete(new ModelRequest("分析订单", "", "prompt-v1")));

        assertTrue(first.retryable());
        assertTrue(second.retryable());
        assertEquals(1, calls.get(), "熔断后不应继续发送网络请求");
    }

    @Test
    void shouldRejectMalformedResponseWithoutRetrying() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"choices\":[]}");
        });
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 3, null, null));

        ModelGatewayException exception = assertThrows(ModelGatewayException.class,
                () -> gateway.complete(new ModelRequest("分析订单", "", "prompt-v1")));

        assertFalse(exception.retryable());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldAdaptToolNamesAndLegacyTextContractsForStrictProviders() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"model":"provider-model","choices":[{"message":{"content":null,
                    "tool_calls":[{"id":"call-1","type":"function","function":{"name":"workspace_read",
                    "arguments":"{\\"path\\":\\"src/App.java\\"}"}},{"id":"call-2","type":"function",
                    "function":{"name":"demo_echo","arguments":"{\\"input\\":\\"hello\\"}"}}]}}],
                    "usage":{"input_tokens":30,"output_tokens":12}}
                    """);
        });
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null));

        ModelResponse response = gateway.complete(new ModelRequest(
                "读取项目入口", "", "prompt-agent",
                List.of(
                        new ModelToolDefinition("workspace.read", "读取文件",
                                java.util.Map.of("type", "object")),
                        new ModelToolDefinition("demo.echo", "返回输入",
                                java.util.Map.of("type", "string", "x-harness-legacy-text", true))
                )));

        assertTrue(requestBody.get().contains("workspace_read"));
        assertTrue(requestBody.get().contains("demo_echo"));
        assertTrue(requestBody.get().contains("\"input\""));
        assertTrue(requestBody.get().contains("tool_choice"));
        assertTrue(response.hasToolCalls());
        assertEquals("call-1", response.toolCalls().get(0).id());
        assertEquals("workspace.read", response.toolCalls().get(0).name());
        assertEquals("{\"path\":\"src/App.java\"}", response.toolCalls().get(0).arguments());
        assertEquals("demo.echo", response.toolCalls().get(1).name());
        assertEquals("hello", response.toolCalls().get(1).arguments());
        assertEquals("", response.content());
    }

    @Test
    void shouldSendStructuredAssistantAndToolMessages() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, success("目录已经读取完成"));
        });
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null));
        ModelToolCall call = new ModelToolCall("call-list", "workspace.read", "{\"path\":\"README.md\"}");

        ModelResponse response = gateway.complete(new ModelRequest(
                "忽略此兼容输入", "", "prompt-agent",
                List.of(new ModelToolDefinition("workspace.read", "读取文件", java.util.Map.of("type", "object"))),
                List.of(
                        ModelMessage.system("工具历史是有状态的"),
                        ModelMessage.user("请理解项目"),
                        ModelMessage.assistant("", "先保留上一轮思考链", List.of(call)),
                        ModelMessage.tool(call.id(), "{\"entries\":[]}"))));

        assertEquals("目录已经读取完成", response.content());
        assertTrue(requestBody.get().contains("\"role\":\"assistant\""));
        assertTrue(requestBody.get().contains("\"tool_calls\""));
        assertTrue(requestBody.get().contains("\"name\":\"workspace_read\""));
        assertTrue(requestBody.get().contains("\"tool_call_id\":\"call-list\""));
        assertTrue(requestBody.get().contains("\"role\":\"tool\""));
        assertTrue(requestBody.get().contains("\"reasoning_content\":\"先保留上一轮思考链\""));
    }

    @Test
    void shouldParseReasoningContentFromStreamingToolResponse() throws IOException {
        HttpServer server = server(exchange -> respondSse(exchange, """
                data: {"model":"provider-model","choices":[{"delta":{"reasoning_content":"先分析工具结果。"}}]}

                data: {"model":"provider-model","choices":[{"delta":{"reasoning_content":"再决定下一步。"}}]}

                data: {"model":"provider-model","choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-next","function":{"name":"workspace_read","arguments":"{\\"path\\":\\"README.md\\"}"}}]}}]}

                data: [DONE]

                """));
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null));

        ModelResponse response = gateway.completeStreaming(new ModelRequest(
                "继续检查", "", "prompt-agent",
                List.of(new ModelToolDefinition("workspace.read", "读取文件",
                        java.util.Map.of("type", "object")))), ignored -> {
        });

        assertEquals("先分析工具结果。再决定下一步。", response.reasoningContent());
        assertEquals("call-next", response.toolCalls().get(0).id());
        assertEquals("workspace.read", response.toolCalls().get(0).name());
    }

    @Test
    void shouldExposeSanitizedProviderErrorBody() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 400,
                "{\"error\":{\"message\":\"reasoning_content is required; api_key=secret-value\"}}"));
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null));

        ModelGatewayException exception = assertThrows(ModelGatewayException.class,
                () -> gateway.complete(new ModelRequest("继续检查", "", "prompt-agent")));

        assertTrue(exception.getMessage().contains("reasoning_content is required"));
        assertFalse(exception.getMessage().contains("secret-value"));
    }

    private OpenAiCompatibleModelGateway gateway(ModelConfig config) {
        return new OpenAiCompatibleModelGateway(RestClient.builder(), config, sanitizer,
                new HarnessMetrics(new SimpleMeterRegistry()));
    }

    private ModelConfig config(String baseUrl, int maxAttempts, String fallbackBaseUrl, String fallbackName) {
        return config(baseUrl, maxAttempts, fallbackBaseUrl, fallbackName, 3);
    }

    private ModelConfig config(String baseUrl, int maxAttempts, String fallbackBaseUrl, String fallbackName,
                               int circuitFailureThreshold) {
        return new ModelConfig(true, baseUrl, "primary-api-key", "primary-model",
                fallbackBaseUrl, fallbackBaseUrl == null ? null : "fallback-api-key", fallbackName,
                maxAttempts, 0, circuitFailureThreshold, 60_000,
                new BigDecimal("0.5"), new BigDecimal("1.0"), 10_000);
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> handler.handle(exchange));
        server.start();
        servers.add(server);
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String success(String content) {
        return "{\"model\":\"provider-model\",\"choices\":[{\"message\":{\"content\":\""
                + content + "\"}}],\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void respondSse(HttpExchange exchange, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
