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
import java.util.concurrent.atomic.AtomicInteger;

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

        RunDetail firstResult = runService.start(created.id(), "tenant-demo");
        assertEquals(RunStatus.SUCCEEDED, firstResult.run().status(), firstResult.run().error() + " / " + firstResult.steps());
        assertEquals("检查订单状态", firstResult.run().output());
        assertEquals(StepStatus.SUCCEEDED, firstResult.steps().get(0).status());
        assertEquals(StepStatus.SUCCEEDED, firstResult.steps().get(1).status());
        assertEquals(1, firstResult.steps().get(1).attempt());

        RunDetail secondResult = runService.start(created.id(), "tenant-demo");
        assertEquals(1, secondResult.steps().get(1).attempt());
        long successEvents = auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "STEP_SUCCEEDED".equals(event.getEventType()))
                .count();
        assertEquals(2, successEvents);
    }

    @Test
    void shouldPersistFailureStateAndAuditEvent() {
        RunSummary created = runService.create(request("test.failure", "触发失败"));
        RunDetail result = runService.start(created.id(), "tenant-demo");

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

    @Test
    void shouldReturnSameRunForRepeatedIdempotencyKey() {
        CreateRunRequest request = request("demo.echo", "幂等执行")
                .withIdempotencyKey("request-123");

        RunSummary first = runService.create(request);
        RunSummary second = runService.create(request);

        assertEquals(first.id(), second.id());
        assertEquals("request-123", second.idempotencyKey());
    }

    @Test
    void shouldRejectReusingIdempotencyKeyForDifferentRequest() {
        runService.create(request("demo.echo", "原始输入").withIdempotencyKey("request-456"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> runService.create(request("demo.echo", "变更输入").withIdempotencyKey("request-456"))
        );

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
    }

    @Test
    void shouldPauseForApprovalAndResumeAfterApproval() {
        RunSummary created = runService.create(request("demo.approval", "执行高风险演示操作"));

        RunDetail waiting = runService.start(created.id(), "tenant-demo");
        assertEquals(RunStatus.WAITING_APPROVAL, waiting.run().status());
        assertEquals(StepStatus.WAITING_APPROVAL, waiting.steps().get(1).status());

        RunDetail completed = runService.approve(created.id(), "tenant-demo");
        assertEquals(RunStatus.SUCCEEDED, completed.run().status());
        assertEquals(StepStatus.SUCCEEDED, completed.steps().get(1).status());
        assertEquals(1, completed.steps().get(1).attempt());
        assertEquals(1, auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "APPROVAL_APPROVED".equals(event.getEventType()))
                .count());
    }

    @Test
    void shouldBlockCrossTenantAccess() {
        RunSummary created = runService.create(request("demo.echo", "跨租户访问"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> runService.start(created.id(), "another-tenant")
        );
        assertEquals("TENANT_ACCESS_DENIED", exception.getCode());
    }

    @Test
    void shouldRejectApprovalAsFailure() {
        RunSummary created = runService.create(request("demo.approval", "拒绝高风险操作"));
        runService.start(created.id(), "tenant-demo");

        RunDetail rejected = runService.reject(created.id(), "tenant-demo", "风险未确认");
        assertEquals(RunStatus.FAILED, rejected.run().status());
        assertEquals(StepStatus.FAILED, rejected.steps().get(1).status());
        assertEquals("风险未确认", rejected.run().error());
    }

    @Test
    void shouldRetryTransientFailure() {
        RunSummary created = runService.create(request("test.flaky", "重试瞬态错误"));
        RunDetail firstResult = runService.start(created.id(), "tenant-demo");
        assertEquals(RunStatus.FAILED, firstResult.run().status());

        RunDetail retried = runService.retry(created.id(), "tenant-demo");
        assertEquals(RunStatus.SUCCEEDED, retried.run().status());
        assertEquals(2, retried.steps().get(1).attempt());
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
                    return new ToolDefinition("test.failure", "测试失败工具", true, "LOW", false, Map.of());
                }

                @Override
                public String execute(String input) {
                    throw new IllegalStateException("测试工具执行失败");
                }
            };
        }

        @Bean
        HarnessTool flakyTool() {
            AtomicInteger attempts = new AtomicInteger();
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.flaky", "测试瞬态失败工具", true, "LOW", false, Map.of());
                }

                @Override
                public String execute(String input) {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("瞬态工具错误");
                    }
                    return "重试成功: " + input;
                }
            };
        }
    }
}
