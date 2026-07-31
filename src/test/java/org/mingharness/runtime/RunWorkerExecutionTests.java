package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.messaging.RunExecutionMessage;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.application.RunExecutionStateService;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.HarnessTool;
import org.mingharness.tool.RetryableToolException;
import org.mingharness.tool.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Worker 在短事务状态边界下仍能持久化业务失败和完整审计。 */
@SpringBootTest
@Import(RunWorkerExecutionTests.WorkerToolConfiguration.class)
class RunWorkerExecutionTests {

    @Autowired
    private RunService runService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private RunExecutionStateService executionStateService;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private WorkerFlakyState workerFlakyState;

    @BeforeEach
    void cleanDatabase() {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        workerFlakyState.reset();
    }

    @Test
    void shouldPersistWorkerFailureAfterToolException() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-worker", "worker-user", "Worker 失败持久化", "触发 Worker 工具错误",
                "test.worker-failure", null, "prompt-v1", "policy-v1", BigDecimal.ONE));
        Run run = runRepository.findById(created.id()).orElseThrow();
        run.start();
        runRepository.saveAndFlush(run);

        runService.executeFromWorker(new RunExecutionMessage(
                "worker-failure-event", run.getId(), run.getTenantId(), run.getTraceId(),
                "START", Instant.now()));

        Run failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertEquals(StepStatus.FAILED, failed.getSteps().get(1).getStatus());
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .anyMatch(event -> "RUN_FAILED".equals(event.getEventType())));
    }

    @Test
    void shouldRetryReadOnlyToolInsideShortTransactionBoundaries() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-worker-retry", "worker-user", "Worker 工具重试", "触发一次瞬态错误",
                "test.worker-flaky", null, "prompt-v1", "policy-v1", BigDecimal.ONE));
        Run run = runRepository.findById(created.id()).orElseThrow();
        run.start();
        runRepository.saveAndFlush(run);

        runService.executeFromWorker(new RunExecutionMessage(
                "worker-flaky-event", run.getId(), run.getTenantId(), run.getTraceId(),
                "START", Instant.now()));

        Run completed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.SUCCEEDED, completed.getStatus());
        assertEquals(2, completed.getSteps().get(1).getAttempt());
        assertEquals(2, workerFlakyState.invocations());
    }

    @Test
    void shouldFailQueuedStepsWhenDispatchHasExhaustedRetries() {
        Run run = new Run("tenant-dispatch", "worker-user", "消息投递失败", "输入",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        run.addStep(new org.mingharness.runtime.domain.Step(
                1, org.mingharness.runtime.domain.StepType.MODEL, "model.complete", "输入"));
        run.start();
        runRepository.saveAndFlush(run);

        assertTrue(executionStateService.failAfterDispatchFailure(
                run.getId(), run.getTenantId(), "RabbitMQ 发布确认失败"));

        Run failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertEquals(StepStatus.FAILED, failed.getSteps().get(0).getStatus());
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .anyMatch(event -> "RUN_DISPATCH_FAILED".equals(event.getEventType())));
    }

    @TestConfiguration
    static class WorkerToolConfiguration {

        @Bean
        HarnessTool failingWorkerTool() {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.worker-failure", "Worker 失败测试工具",
                            true, "LOW", false, Map.of());
                }

                @Override
                public String execute(String input) {
                    throw new IllegalStateException("Worker 工具执行失败");
                }
            };
        }

        @Bean
        WorkerFlakyState workerFlakyState() {
            return new WorkerFlakyState();
        }

        @Bean
        HarnessTool flakyWorkerTool(WorkerFlakyState state) {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.worker-flaky", "Worker 瞬态重试工具",
                            true, "LOW", false, Map.of(), java.util.Set.of(),
                            30_000, 2, "DENY_EXTERNAL", Map.of());
                }

                @Override
                public String execute(String input) {
                    if (state.invocations.incrementAndGet() == 1) {
                        throw new RetryableToolException("模拟瞬态工具错误");
                    }
                    return "重试成功: " + input;
                }
            };
        }
    }

    static class WorkerFlakyState {
        private final AtomicInteger invocations = new AtomicInteger();

        void reset() {
            invocations.set(0);
        }

        int invocations() {
            return invocations.get();
        }
    }
}
