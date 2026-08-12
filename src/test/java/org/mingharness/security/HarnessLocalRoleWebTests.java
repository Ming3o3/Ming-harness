package org.mingharness.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证本地桌面演示的三种身份会通过真实 HTTP 请求映射到不同产品角色。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "harness.auth.mode=local",
        "harness.execution.mode=sync",
        "harness.local-execution.async=false",
        "harness.redis.enabled=false",
        "harness.messaging.enabled=false",
        "harness.model.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HarnessLocalRoleWebTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldMapLocalDemoRoleHeaderToCurrentUserView() throws Exception {
        assertRole("ADMIN", "admin-demo", "管理员");
        assertRole("TEACHER", "teacher-demo", "老师");
        assertRole("STUDENT", "student-demo", "学生");
    }

    private void assertRole(String role, String userId, String ignoredLabel) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                        .header("X-Tenant-Id", "tenant-demo")
                        .header("X-User-Id", userId)
                        .header("X-Harness-Role", role)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"primaryRole\":\"" + role + "\""), response.body());
        assertTrue(response.body().contains("\"userId\":\"" + userId + "\""), response.body());
        assertTrue(response.body().contains("\"localDemo\":true"), response.body());
    }
}
