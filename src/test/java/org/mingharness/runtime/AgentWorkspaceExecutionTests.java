package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.model.ModelGateway;
import org.mingharness.model.ModelMessage;
import org.mingharness.model.ModelResponse;
import org.mingharness.model.ModelToolCall;
import org.mingharness.model.DemoModelGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证默认演示模型在真实 Worker 编排中完成工作区理解闭环。 */
@SpringBootTest
@Import(AgentWorkspaceExecutionTests.AgentGitFallbackModelConfiguration.class)
class AgentWorkspaceExecutionTests {

    private static final Path WORKSPACE_ROOT = createWorkspaceRoot();

    @Autowired
    private RunService runService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @DynamicPropertySource
    static void configureWorkspace(DynamicPropertyRegistry registry) {
        registry.add("harness.workspace.enabled", () -> true);
        registry.add("harness.workspace.root", () -> WORKSPACE_ROOT.toString());
    }

    @BeforeEach
    void resetWorkspaceAndRuns() throws IOException {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        if (Files.exists(WORKSPACE_ROOT)) {
            try (var paths = Files.walk(WORKSPACE_ROOT)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(WORKSPACE_ROOT))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException exception) {
                                throw new IllegalStateException(exception);
                            }
                        });
            }
        }
        Files.createDirectories(WORKSPACE_ROOT.resolve("src/main"));
        Files.writeString(WORKSPACE_ROOT.resolve("src/main/App.java"),
                "package demo;\npublic class App { void run() {} }\n");
    }

    @Test
    void shouldExecuteListSearchReadAndFinalModelInOneAgentRun() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-agent-workspace", "operator", "演示工作区理解", "请理解项目入口",
                null, null, "prompt-agent", "policy-v1", java.math.BigDecimal.TEN,
                null, "workspace.read", true, 8));

        RunDetail detail = runService.start(created.id(), "tenant-agent-workspace");

        assertEquals(RunStatus.SUCCEEDED, detail.run().status(), detail.run().error());
        assertTrue(detail.steps().stream().anyMatch(step -> "workspace.list".equals(step.name())));
        assertTrue(detail.steps().stream().anyMatch(step -> "workspace.search".equals(step.name())));
        assertTrue(detail.steps().stream().anyMatch(step -> "workspace.read".equals(step.name())));
        assertTrue(detail.run().output().contains("src/main/App.java"));
        assertTrue(detail.run().output().contains("public class App"));
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .anyMatch(event -> "RUN_SUCCEEDED".equals(event.getEventType())));
    }

    @Test
    void shouldRecoverFromGitUnavailableAndContinueReadingWorkspace() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-agent-git-fallback", "operator", "Git 降级工作区理解", "请检查没有 Git 的项目",
                null, null, "prompt-agent", "policy-v1", java.math.BigDecimal.TEN,
                null, "workspace.read", true, 6));

        RunDetail detail = runService.start(created.id(), "tenant-agent-git-fallback");

        assertEquals(RunStatus.SUCCEEDED, detail.run().status(), detail.run().error());
        assertTrue(detail.steps().stream().anyMatch(step -> "workspace.git.status".equals(step.name())));
        assertTrue(detail.steps().stream().anyMatch(step -> "workspace.list".equals(step.name())));
        assertTrue(detail.steps().stream().anyMatch(step -> "workspace.read".equals(step.name())));
        assertTrue(detail.steps().stream()
                .filter(step -> "workspace.git.status".equals(step.name()))
                .findFirst().orElseThrow().output().contains("\"available\":false"));
        assertTrue(detail.steps().stream()
                .filter(step -> "workspace.read".equals(step.name()))
                .findFirst().orElseThrow().output().contains("public class App"));
    }

    @TestConfiguration
    static class AgentGitFallbackModelConfiguration {

        @Bean
        @Primary
        ModelGateway gitFallbackModelGateway() {
            DemoModelGateway fallback = new DemoModelGateway();
            return request -> {
                if (request.input() == null || !request.input().contains("没有 Git")) {
                    return fallback.complete(request);
                }
                String latestTool = latestToolName(request.messages());
                if (latestTool.isBlank()) {
                    return toolResponse("git-status", "workspace.git.status", "{}");
                }
                if ("workspace.git.status".equals(latestTool)) {
                    return toolResponse("list-after-git-fallback", "workspace.list",
                            "{\"path\":\".\",\"recursive\":false}");
                }
                if ("workspace.list".equals(latestTool)) {
                    return toolResponse("read-after-git-fallback", "workspace.read",
                            "{\"path\":\"src/main/App.java\",\"startLine\":1,\"endLine\":120}");
                }
                if ("workspace.read".equals(latestTool)) {
                    return new ModelResponse("已读取项目文件并完成检查", "git-fallback-model",
                            request.promptVersion(), 5, 5);
                }
                return new ModelResponse("已完成检查", "git-fallback-model", request.promptVersion(), 5, 5);
            };
        }

        private ModelResponse toolResponse(String id, String name, String arguments) {
            return new ModelResponse("继续检查工作区", "git-fallback-model", "prompt-agent", 5, 5,
                    java.math.BigDecimal.ZERO, java.util.List.of(new ModelToolCall(id, name, arguments)));
        }

        private String latestToolName(java.util.List<ModelMessage> messages) {
            if (messages == null) return "";
            for (int index = messages.size() - 1; index >= 0; index--) {
                ModelMessage message = messages.get(index);
                if (!"tool".equals(message.role())) continue;
                for (int previous = index - 1; previous >= 0; previous--) {
                    ModelMessage assistant = messages.get(previous);
                    if (!"assistant".equals(assistant.role())) continue;
                    for (ModelToolCall call : assistant.toolCalls()) {
                        if (message.toolCallId().equals(call.id())) return call.name();
                    }
                }
            }
            return "";
        }
    }

    private static Path createWorkspaceRoot() {
        try {
            return Files.createTempDirectory("ming-harness-agent-workspace-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
