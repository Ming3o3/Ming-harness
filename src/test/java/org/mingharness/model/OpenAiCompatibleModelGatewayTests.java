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
    void shouldSendToolDefinitionsAndParseNativeToolCalls() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"model":"provider-model","choices":[{"message":{"content":null,
                    "tool_calls":[{"id":"call-1","type":"function","function":{"name":"workspace.read",
                    "arguments":"{\\"path\\":\\"src/App.java\\"}"}}]}}],
                    "usage":{"input_tokens":30,"output_tokens":12}}
                    """);
        });
        OpenAiCompatibleModelGateway gateway = gateway(config(url(server), 1, null, null));

        ModelResponse response = gateway.complete(new ModelRequest(
                "读取项目入口", "", "prompt-agent",
                List.of(new ModelToolDefinition("workspace.read", "读取文件",
                        java.util.Map.of("type", "object")))));

        assertTrue(requestBody.get().contains("workspace.read"));
        assertTrue(requestBody.get().contains("tool_choice"));
        assertTrue(response.hasToolCalls());
        assertEquals("call-1", response.toolCalls().get(0).id());
        assertEquals("workspace.read", response.toolCalls().get(0).name());
        assertEquals("{\"path\":\"src/App.java\"}", response.toolCalls().get(0).arguments());
        assertEquals("", response.content());
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

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
