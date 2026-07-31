package org.mingharness.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证认证拦截器已经接入真实 Spring MVC 请求链。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "harness.auth.mode=api-key",
        "harness.auth.api-keys=web-test-key|tenant-web|web-user|tool.read,run.read,ops.read",
        "management.endpoint.health.show-details=when_authorized",
        "management.endpoint.health.show-components=when_authorized"
})
class HarnessAuthWebTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldReturnStructuredAuthenticationErrorWithoutApiKey() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tools")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("AUTHENTICATION_REQUIRED"));
        assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
        assertTrue(response.headers().firstValue("X-Trace-Id").isPresent());
        assertTrue(response.body().contains("traceId"));
    }

    @Test
    void shouldAuthorizeApiKeyAndBindTenantContext() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tools"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("X-Trace-Id").isPresent());
    }

    @Test
    void shouldExposeOnlyOverallHealthToAnonymousProbe() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\""));
        assertTrue(!response.body().contains("\"components\""));
    }

    @Test
    void shouldProtectActuatorMetricsAndReturnSafeHealthSummary() throws Exception {
        HttpResponse<String> unauthorized = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/metrics/harness.runs.created"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, unauthorized.statusCode());

        HttpResponse<String> health = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/health"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\""));
        assertTrue(health.body().contains("\"db\""));
        assertTrue(!health.body().contains("jdbc:h2"));

        HttpResponse<String> metrics = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/metrics/harness.runs.created"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metrics.statusCode(), metrics.body());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
