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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    private static Path createWorkspaceRoot() {
        try {
            return Files.createTempDirectory("ming-harness-agent-workspace-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
