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
        "harness.auth.api-keys=web-test-key|tenant-web|web-user|tool.read,run.read,ops.read,tenant.policy.read,tenant.policy.write,auth.key.read,auth.key.manage",
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
    void shouldExposeTenantScopedRunPageWithoutBreakingLegacyList() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/runs/page?page=0&size=1"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"items\""));
        assertTrue(response.body().contains("\"totalElements\""));
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

    @Test
    void shouldExposeAndUpdateOwnTenantPolicy() throws Exception {
        HttpResponse<String> update = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/tenants/tenant-web/policy"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(
                                "{\"maxActiveRuns\":5,\"maxStepsPerRun\":10,\"maxInputLength\":5000,"
                                        + "\"maxBudget\":50,\"maxCreatesPerMinute\":20}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, update.statusCode(), update.body());
        assertTrue(update.body().contains("\"defaulted\":false"));
        assertTrue(update.body().contains("\"maxActiveRuns\":5"));

        HttpResponse<String> read = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/tenants/tenant-web/policy"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, read.statusCode());
        assertTrue(read.body().contains("\"maxBudget\":50"));
    }

    @Test
    void shouldRejectCrossTenantPolicyWithoutExplicitScope() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/tenants/another-tenant/policy"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("TENANT_POLICY_SCOPE_DENIED"));
    }

    @Test
    void shouldCreateRotateUseAndRevokeDatabaseApiKeyWithoutReturningSecretAgain() throws Exception {
        HttpResponse<String> created = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/api-keys"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"tenantId\":\"tenant-web\",\"userId\":\"generated-user\","
                                        + "\"permissions\":[\"tool.read\"]}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, created.statusCode(), created.body());
        assertTrue(created.body().contains("\"secret\":\"mh_"));
        assertTrue(created.body().contains("\"status\":\"ACTIVE\""));
        String secret = created.body().replaceFirst(".*\\\"secret\\\":\\\"(mh_[^\\\"]+).*", "$1");
        String keyId = created.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        HttpResponse<String> tools = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tools"))
                        .header("Authorization", "Bearer " + secret).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, tools.statusCode(), tools.body());

        HttpResponse<String> listed = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/api-keys"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listed.statusCode());
        assertTrue(listed.body().contains(keyId));
        assertTrue(!listed.body().contains(secret));

        HttpResponse<String> audits = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/api-keys/audits"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, audits.statusCode(), audits.body());
        assertTrue(audits.body().contains("API_KEY_CREATED"));

        HttpResponse<String> rotated = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/api-keys/" + keyId + "/rotate"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rotated.statusCode(), rotated.body());
        String rotatedSecret = rotated.body().replaceFirst(".*\\\"secret\\\":\\\"(mh_[^\\\"]+).*", "$1");
        String rotatedKeyId = rotated.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        assertTrue(rotatedSecret.startsWith("mh_"));
        assertTrue(!secret.equals(rotatedSecret));

        HttpResponse<String> oldSecretRejected = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tools"))
                        .header("Authorization", "Bearer " + secret).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, oldSecretRejected.statusCode());

        HttpResponse<String> rotatedSecretWorks = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tools"))
                        .header("Authorization", "Bearer " + rotatedSecret).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rotatedSecretWorks.statusCode(), rotatedSecretWorks.body());

        HttpResponse<String> revoked = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/admin/api-keys/" + rotatedKeyId))
                        .header("Authorization", "Bearer web-test-key").DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, revoked.statusCode(), revoked.body());
        assertTrue(revoked.body().contains("\"status\":\"REVOKED\""));

        HttpResponse<String> rejected = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tools"))
                        .header("Authorization", "Bearer " + rotatedSecret).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, rejected.statusCode());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
