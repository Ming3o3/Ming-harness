package org.mingharness.context;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.EmbeddingProperties;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
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

class OpenAiCompatibleEmbeddingGatewayTests {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void shouldReturnVectorsInInputOrderAndSanitizePayload() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"model":"embedding-model","data":[
                      {"index":1,"embedding":[0.3,0.4]},
                      {"index":0,"embedding":[0.1,0.2]}]}
                    """);
        });
        OpenAiCompatibleEmbeddingGateway gateway = gateway(properties(url(server), true, 2, 1));

        List<EmbeddingVector> result = gateway.embed(List.of("api_key=secret", "第二段"));

        assertEquals(2, result.size());
        assertEquals(List.of(0.1, 0.2), result.get(0).values());
        assertEquals("embedding-model", result.get(1).model());
        assertFalse(requestBody.get().contains("secret"));
        assertTrue(requestBody.get().contains("[REDACTED]"));
    }

    @Test
    void shouldRetryTemporaryFailure() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 503, "temporary");
            } else {
                respond(exchange, 200, "{\"model\":\"m\",\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}");
            }
        });
        OpenAiCompatibleEmbeddingGateway gateway = gateway(properties(url(server), true, 2, 2));

        assertEquals(1, gateway.embed(List.of("query")).size());
        assertEquals(2, calls.get());
    }

    @Test
    void shouldRejectWrongDimensionAndDisabledCalls() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200,
                "{\"model\":\"m\",\"data\":[{\"index\":0,\"embedding\":[0.1]}]}"));
        OpenAiCompatibleEmbeddingGateway gateway = gateway(properties(url(server), true, 2, 1));

        EmbeddingGatewayException exception = assertThrows(EmbeddingGatewayException.class,
                () -> gateway.embed(List.of("query")));
        assertFalse(exception.retryable());

        OpenAiCompatibleEmbeddingGateway disabled = gateway(properties(url(server), false, 2, 1));
        assertTrue(disabled.embed(List.of("query")).isEmpty());
    }

    private OpenAiCompatibleEmbeddingGateway gateway(EmbeddingProperties properties) {
        return new OpenAiCompatibleEmbeddingGateway(properties, RestClient.builder(),
                new SensitiveDataSanitizer(), new ObjectMapper());
    }

    private EmbeddingProperties properties(String baseUrl, boolean enabled, int dimension, int attempts) {
        return new EmbeddingProperties(enabled, baseUrl, "embedding-api-key", "embedding-model",
                dimension, 8, 1_000, 100_000, attempts, 0, 10_000);
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> handler.handle(exchange));
        server.start();
        servers.add(server);
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
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
