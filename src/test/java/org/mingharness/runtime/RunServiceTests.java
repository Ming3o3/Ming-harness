package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.HarnessTool;
import org.mingharness.tool.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(RunServiceTests.FailureToolConfiguration.class)
class RunServiceTests {

    @Autowired
    private RunService runService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void cleanDatabase() {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldCreateAndExecuteRunIdempotently() {
        RunSummary created = runService.create(request("demo.echo", "检查订单状态"));
        assertEquals(RunStatus.QUEUED, created.status());
        assertEquals(2, created.stepCount());

        RunDetail firstResult = runService.start(created.id());
        assertEquals(RunStatus.SUCCEEDED, firstResult.run().status(), firstResult.run().error() + " / " + firstResult.steps());
        assertEquals("检查订单状态", firstResult.run().output());
        assertEquals(StepStatus.SUCCEEDED, firstResult.steps().get(0).status());
        assertEquals(StepStatus.SUCCEEDED, firstResult.steps().get(1).status());
        assertEquals(1, firstResult.steps().get(1).attempt());

        RunDetail secondResult = runService.start(created.id());
        assertEquals(1, secondResult.steps().get(1).attempt());
        long successEvents = auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "STEP_SUCCEEDED".equals(event.getEventType()))
                .count();
        assertEquals(2, successEvents);
    }

    @Test
    void shouldPersistFailureStateAndAuditEvent() {
        RunSummary created = runService.create(request("test.failure", "触发失败"));
        RunDetail result = runService.start(created.id());

        assertEquals(RunStatus.FAILED, result.run().status());
        assertEquals(StepStatus.SUCCEEDED, result.steps().get(0).status());
        assertEquals(StepStatus.FAILED, result.steps().get(1).status());
        assertEquals("测试工具执行失败", result.run().error());
        long failureEvents = auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "RUN_FAILED".equals(event.getEventType()))
                .count();
        assertEquals(1, failureEvents);
    }

    @Test
    void shouldRejectUnknownTool() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> runService.create(request("missing.tool", "无效工具"))
        );
        assertEquals("TOOL_NOT_FOUND", exception.getCode());
    }

    private CreateRunRequest request(String toolName, String input) {
        return new CreateRunRequest(
                "tenant-demo",
                "user-demo",
                "测试任务",
                input,
                toolName,
                null,
                null,
                null,
                BigDecimal.TEN
        );
    }

    @TestConfiguration
    static class FailureToolConfiguration {

        @Bean
        HarnessTool failureTool() {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.failure", "测试失败工具", true, "LOW", Map.of());
                }

                @Override
                public String execute(String input) {
                    throw new IllegalStateException("测试工具执行失败");
                }
            };
        }
    }
}
