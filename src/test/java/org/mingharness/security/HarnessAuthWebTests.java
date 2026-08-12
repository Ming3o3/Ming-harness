package org.mingharness.security;

import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证认证拦截器已经接入真实 Spring MVC 请求链。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "harness.auth.mode=api-key",
        "harness.auth.api-keys=web-test-key|tenant-web|web-user|tool.read,run.read,run.create,ops.read,model.configure,context.read,context.write,context.configure,workspace.read,workspace.write,workspace.manage,tenant.policy.read,tenant.policy.write,auth.key.read,auth.key.manage,context.reindex;web-other-key|tenant-other|other-user|run.read,workspace.read",
        "harness.workspace.enabled=true",
        "harness.workspace.local-registration-enabled=true",
        "management.endpoint.health.show-details=when_authorized",
        "management.endpoint.health.show-components=when_authorized"
})
class HarnessAuthWebTests {

    @LocalServerPort
    private int port;

    @TempDir
    Path tempDir;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldExposeTrustedIdentityAndDerivedProductRoles() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/me"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"tenantId\":\"tenant-web\""), response.body());
        assertTrue(response.body().contains("\"userId\":\"web-user\""), response.body());
        assertTrue(response.body().contains("\"primaryRole\":\"ADMIN\""), response.body());
        assertTrue(response.body().contains("\"localDemo\":false"), response.body());
        assertTrue(response.body().contains("\"ADMIN\""), response.body());
        assertTrue(response.body().contains("\"STUDENT\""), response.body());
        assertFalse(response.body().contains("\"TEACHER\""), response.body());
    }

    @Test
    void shouldUploadDocxThroughAuthenticatedKnowledgeDocumentEndpoint() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("真实 HTTP 导入的发布规则");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            docx = output.toByteArray();
        }
        String boundary = "----MingHarness" + UUID.randomUUID();
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/documents/upload"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipartDocumentBody(boundary, docx)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode(), response.body());
        assertTrue(response.body().contains("真实 HTTP 导入的发布规则"), response.body());
        assertTrue(response.body().contains("\"title\":\"http-release-rules\""), response.body());
    }

    @Test
    void shouldProtectContextReindexWithDedicatedPermission() throws Exception {
        HttpResponse<String> authorized = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/reindex"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, authorized.statusCode(), authorized.body());
        assertTrue(authorized.body().contains("\"embeddingReady\":false"), authorized.body());

        HttpResponse<String> denied = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/reindex"))
                        .header("Authorization", "Bearer web-other-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode(), denied.body());
        assertTrue(denied.body().contains("PERMISSION_DENIED"), denied.body());
    }

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
    void shouldSaveUserModelConfigWithoutReturningApiKey() throws Exception {
        HttpResponse<String> saved = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/model-config"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true,\"baseUrl\":\"http://localhost:11434/v1\","
                                + "\"modelName\":\"qwen2.5-coder\",\"apiKey\":\"secret-web-model\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, saved.statusCode(), saved.body());
        assertTrue(saved.body().contains("\"source\":\"user\""), saved.body());
        assertTrue(saved.body().contains("••••odel"), saved.body());
        assertFalse(saved.body().contains("secret-web-model"), saved.body());

        HttpResponse<String> loaded = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/model-config"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loaded.statusCode(), loaded.body());
        assertFalse(loaded.body().contains("secret-web-model"), loaded.body());

        HttpResponse<String> denied = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/model-config"))
                        .header("Authorization", "Bearer web-other-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode(), denied.body());
        assertTrue(denied.body().contains("PERMISSION_DENIED"), denied.body());
    }

    @Test
    void shouldTestDisabledModelConfigWithoutCallingProvider() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/model-config/test"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"enabled\":false,\"baseUrl\":\"\",\"modelName\":\"\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"DISABLED\""), response.body());
        assertTrue(response.body().contains("未发起网络请求"), response.body());
    }

    @Test
    void shouldSaveEmbeddingConfigWithoutReturningApiKey() throws Exception {
        HttpResponse<String> saved = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/embedding-config"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":true,\"baseUrl\":\"http://localhost:11434/v1\","
                                + "\"modelName\":\"nomic-embed-text\",\"modelVersion\":\"v1\",\"dimension\":1536,"
                                + "\"apiKey\":\"secret-web-embedding\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, saved.statusCode(), saved.body());
        assertTrue(saved.body().contains("\"source\":\"tenant\""), saved.body());
        assertTrue(saved.body().contains("••••ding"), saved.body());
        assertFalse(saved.body().contains("secret-web-embedding"), saved.body());

        HttpResponse<String> loaded = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/embedding-config"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loaded.statusCode(), loaded.body());
        assertFalse(loaded.body().contains("secret-web-embedding"), loaded.body());
    }

    @Test
    void shouldProtectEmbeddingConfigWithContextConfigurePermission() throws Exception {
        HttpResponse<String> denied = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/embedding-config"))
                        .header("Authorization", "Bearer web-other-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode(), denied.body());
        assertTrue(denied.body().contains("PERMISSION_DENIED"), denied.body());
    }

    @Test
    void shouldTestDisabledEmbeddingConfigWithoutCallingProvider() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/context/embedding-config/test"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"enabled\":false,\"baseUrl\":\"\",\"modelName\":\"\",\"dimension\":1536}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"DISABLED\""), response.body());
        assertTrue(response.body().contains("请先启用外部 Embedding"), response.body());
    }

    @Test
    void shouldExposeSafeLocalWorkspaceSummaryWithoutAbsolutePath() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace"))
                        .header("Authorization", "Bearer web-test-key")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"absolutePathHidden\":true"));
        assertTrue(!response.body().contains("\"rootPath\""));
        assertTrue(!response.body().contains("jdbc:h2"));
    }

    @Test
    void shouldRegisterDesktopWorkspaceWithoutReturningItsAbsolutePath() throws Exception {
        String root = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).toRealPath().toString();
        String jsonPath = root.replace("\\", "\\\\");
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspaces"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .header("X-Harness-Desktop-Bridge", "test-desktop-bridge-token")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"displayName\":\"桌面测试项目\",\"rootPath\":\"" + jsonPath + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"displayName\":\"桌面测试项目\""));
        assertTrue(!response.body().contains(root));
        assertTrue(!response.body().contains("rootPath"));
    }

    @Test
    void shouldRejectBrowserWorkspaceRegistrationWithoutDesktopBridgeToken() throws Exception {
        String root = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).toRealPath().toString();
        String jsonPath = root.replace("\\", "\\\\");
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspaces"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"displayName\":\"浏览器请求\",\"rootPath\":\"" + jsonPath + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.body().contains("DESKTOP_BRIDGE_DENIED"));
    }

    @Test
    void shouldBrowseOnlyOwnWorkspaceWithRelativePathsAndRedactedPreview() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("desktop-project"));
        Path project = Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(project.resolve("App.java"), "class App { String api_key = \"sk-1234567890abcdef\"; }\n");
        runGit(projectRoot, "init", "-q");
        runGit(projectRoot, "add", "src/App.java");
        runGit(projectRoot, "-c", "user.name=Harness Test", "-c", "user.email=harness@example.com",
                "commit", "-qm", "initial");
        Files.writeString(project.resolve("App.java"), "class App { String api_key = \"sk-1234567890abcdef\"; // changed\n}\n");
        Files.writeString(projectRoot.resolve(".env"), "SECRET=do-not-show\n");
        String workspaceId = registerWorkspace(projectRoot, "浏览项目");

        HttpResponse<String> directory = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files?workspaceId=" + workspaceId
                                + "&path=."))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, directory.statusCode(), directory.body());
        assertTrue(directory.body().contains("\"path\":\"src\""));
        assertTrue(directory.body().contains("\"available\":true"));
        assertTrue(directory.body().contains("\"clean\":false"));
        assertFalse(directory.body().contains(".env"));
        assertFalse(directory.body().contains(tempDir.toString()));

        HttpResponse<String> content = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files/content?workspaceId=" + workspaceId
                                + "&path=src/App.java"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, content.statusCode(), content.body());
        assertTrue(content.body().contains("App.java"));
        assertTrue(content.body().contains("\"redacted\":true"));
        assertFalse(content.body().contains("sk-1234567890abcdef"));
        assertFalse(content.body().contains(tempDir.toString()));

        HttpResponse<String> gitStatus = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/git/status?workspaceId=" + workspaceId))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, gitStatus.statusCode(), gitStatus.body());
        assertTrue(gitStatus.body().contains("\"path\":\"src/App.java\""));
        assertTrue(gitStatus.body().contains("\"changeCount\":2"));
        assertTrue(gitStatus.body().contains("\"protectedChangeCount\":1"));
        assertFalse(gitStatus.body().contains(".env"));
        assertFalse(gitStatus.body().contains(tempDir.toString()));

        HttpResponse<String> gitDiff = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/git/diff?workspaceId=" + workspaceId
                                + "&path=src/App.java&contextLines=1"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, gitDiff.statusCode(), gitDiff.body());
        assertTrue(gitDiff.body().contains("\"hasChanges\":true"));
        assertTrue(gitDiff.body().contains("\"contextLines\":1"));
        assertFalse(gitDiff.body().contains("sk-1234567890abcdef"));
        assertFalse(gitDiff.body().contains(tempDir.toString()));

        HttpResponse<String> crossTenant = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files?workspaceId=" + workspaceId
                                + "&path=."))
                        .header("Authorization", "Bearer web-other-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, crossTenant.statusCode(), crossTenant.body());
        assertTrue(crossTenant.body().contains("WORKSPACE_NOT_FOUND"));
    }

    @Test
    void shouldReadOriginalEditorContentAndSaveWithOptimisticHash() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("editor-project"));
        Path source = projectRoot.resolve("src/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class App {\n  String token = \"sk-editor-secret\";\n}\n");
        String workspaceId = registerWorkspace(projectRoot, "编辑项目");

        HttpResponse<String> editor = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files/editor-content?workspaceId="
                                + workspaceId + "&path=src/App.java"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, editor.statusCode(), editor.body());
        assertTrue(editor.body().contains("sk-editor-secret"), editor.body());
        assertTrue(editor.body().contains("class App {\\n  String token"), editor.body());
        String hash = editor.body().replaceFirst(".*\\\"sha256\\\":\\\"([0-9a-f]{64})\\\".*", "$1");

        String updated = "class App {\n  String token = \"saved\";\n}\n";
        HttpResponse<String> saved = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files/editor-content?workspaceId="
                                + workspaceId))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"path\":\"src/App.java\",\"content\":\""
                                + updated.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")
                                + "\",\"expectedSha256\":\"" + hash + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(updated, Files.readString(source));
    }

    @Test
    void shouldRejectStaleEditorSaveAndMissingWritePermission() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("stale-editor-project"));
        Path source = projectRoot.resolve("App.java");
        Files.writeString(source, "before\n");
        String workspaceId = registerWorkspace(projectRoot, "冲突项目");

        HttpResponse<String> editor = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files/editor-content?workspaceId="
                                + workspaceId + "&path=App.java"))
                        .header("Authorization", "Bearer web-test-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, editor.statusCode(), editor.body());
        String hash = editor.body().replaceFirst(".*\\\"sha256\\\":\\\"([0-9a-f]{64})\\\".*", "$1");
        Files.writeString(source, "changed-by-agent\n");

        HttpResponse<String> stale = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files/editor-content?workspaceId="
                                + workspaceId))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"path\":\"App.java\",\"content\":\"local\\n\","
                                + "\"expectedSha256\":\"" + hash + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, stale.statusCode(), stale.body());
        assertTrue(stale.body().contains("WORKSPACE_FILE_CHANGED"), stale.body());

        HttpResponse<String> denied = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspace/files/editor-content?workspaceId="
                                + workspaceId + "&path=App.java"))
                        .header("Authorization", "Bearer web-other-key").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode(), denied.body());
        assertTrue(denied.body().contains("PERMISSION_DENIED"), denied.body());
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
    void shouldStreamInitialRunSnapshotForAuthorizedTenant() throws Exception {
        String runId = createRunForEventStream();
        HttpResponse<InputStream> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/runs/" + runId + "/events"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type")
                .orElse("").startsWith("text/event-stream"));
        try (InputStream body = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            assertEquals("event:snapshot", reader.readLine());
            String data = reader.readLine();
            assertTrue(data.startsWith("data:"), data);
            assertTrue(data.contains("\"id\":\"" + runId + "\""), data);
        }
    }

    @Test
    void shouldRejectCrossTenantRunEventStream() throws Exception {
        String runId = createRunForEventStream();
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/runs/" + runId + "/events"))
                        .header("Authorization", "Bearer web-other-key")
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.body().contains("TENANT_ACCESS_DENIED"));
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
        assertTrue(health.body().contains("\"model\""));
        assertTrue(health.body().contains("\"education\""));
        assertTrue(health.body().contains("\"apiVersion\":\"education-agent/v1\""));
        assertTrue(health.body().contains("\"courseBoundRunsEnabled\":true"));
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

    private byte[] multipartDocumentBody(String boundary, byte[] docx) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "title", null, "http-release-rules");
        writePart(body, boundary, "sensitivity", null, "INTERNAL");
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"http-release-rules.docx\"\r\n"
                + "Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(docx);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private void writePart(ByteArrayOutputStream body, String boundary, String name,
                           String fileName, String value) throws Exception {
        String disposition = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"";
        if (fileName != null) disposition += "; filename=\"" + fileName + "\"";
        body.write((disposition + "\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    /** 真实 HTTP 创建 Run，确保 SSE 测试同时覆盖认证、路由与持久化读取链路。 */
    private String createRunForEventStream() throws Exception {
        String idempotencyKey = "web-sse-" + UUID.randomUUID();
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/runs"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"tenantId":"tenant-web","userId":"web-user","title":"SSE 实时任务",
                                "input":"验证首个实时快照","toolName":"demo.echo","budget":1,
                                "idempotencyKey":"%s"}
                                """.formatted(idempotencyKey)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        return response.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
    }

    /** 用桌面桥接令牌登记临时项目，测试不会让接口响应携带本机临时目录。 */
    private String registerWorkspace(Path root, String displayName) throws Exception {
        String jsonRoot = root.toRealPath().toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String jsonName = displayName.replace("\\", "\\\\").replace("\"", "\\\"");
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/workspaces"))
                        .header("Authorization", "Bearer web-test-key")
                        .header("Content-Type", "application/json")
                        .header("X-Harness-Desktop-Bridge", "test-desktop-bridge-token")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"" + jsonName
                                + "\",\"rootPath\":\"" + jsonRoot + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        return response.body().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
    }

    /** 使用临时 Git 仓库验证项目浏览摘要，无需依赖开发机的全局 Git 用户配置。 */
    private void runGit(Path directory, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
}
