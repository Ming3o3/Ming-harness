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
import org.mingharness.model.DemoModelGateway;
import org.mingharness.model.ModelGateway;
import org.mingharness.model.ModelRequest;
import org.mingharness.model.ModelResponse;
import org.mingharness.model.ModelToolCall;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Worker 在短事务状态边界下仍能持久化业务失败和完整审计。 */
@SpringBootTest
@Import({RunWorkerExecutionTests.WorkerToolConfiguration.class,
        RunWorkerExecutionTests.AgentWorkerModelConfiguration.class})
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
    void shouldResumeAgentToolCallLoopInWorkerAndPersistAllSteps() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-agent-worker", "worker-user", "Worker Agent 任务", "请读取项目文件",
                null, null, "prompt-agent", "policy-v1", BigDecimal.TEN,
                null, null, true, 3));
        Run run = runRepository.findById(created.id()).orElseThrow();
        run.start();
        runRepository.saveAndFlush(run);

        runService.executeFromWorker(new RunExecutionMessage(
                "agent-worker-event", run.getId(), run.getTenantId(), run.getTraceId(),
                "START", Instant.now()));

        Run completed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.SUCCEEDED, completed.getStatus(), completed.getError());
        assertEquals(3, completed.getSteps().size());
        assertEquals("Worker Agent 最终结果", completed.getOutput());
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .anyMatch(event -> "AGENT_MODEL_TURN_QUEUED".equals(event.getEventType())));
    }

    @Test
    void shouldRejectAgentToolCallBeforeCreatingUnauthorizedToolStep() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-agent-permission", "worker-user", "Agent 权限校验", "请验证权限拒绝",
                null, null, "prompt-agent", "policy-v1", BigDecimal.TEN,
                null, null, true, 3));
        Run run = runRepository.findById(created.id()).orElseThrow();
        run.start();
        runRepository.saveAndFlush(run);

        runService.executeFromWorker(new RunExecutionMessage(
                "agent-permission-event", run.getId(), run.getTenantId(), run.getTraceId(),
                "START", Instant.now()));

        Run failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getSteps().size(), "权限失败应在模型步骤完成前阻止工具步骤入队");
        assertTrue(failed.getSteps().get(0).getError().contains("缺少权限"));
    }

    @Test
    void shouldRequireWorkspaceVerificationBeforeAgentRunCanSucceed() {
        Run run = new Run("tenant-agent-validation", "worker-user", "Agent 修改验证", "输入",
                BigDecimal.TEN, "demo-model", "prompt-agent", "policy-v1",
                null, "workspace.write", true, 3);
        run.addStep(succeededStep(1, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"开始修改\",\"toolCalls\":[]}"));
        run.addStep(succeededStep(2, org.mingharness.runtime.domain.StepType.TOOL,
                "workspace.write", "{\"path\":\"src/App.java\"}"));
        run.addStep(succeededStep(3, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"修改完成\",\"toolCalls\":[]}"));
        run.start();
        runRepository.saveAndFlush(run);

        assertTrue(executionStateService.claim(run.getId(), run.getTenantId(), "validation-worker",
                Instant.now().plusSeconds(30)).isPresent());
        assertFalse(executionStateService.finishSuccess(run.getId(), run.getTenantId(), "validation-worker"));

        Run failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertEquals(StepStatus.FAILED, failed.getSteps().get(2).getStatus());
        assertTrue(failed.getError().contains("重新读取文件"));
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .anyMatch(event -> "AGENT_VALIDATION_REQUIRED".equals(event.getEventType())));
    }

    @Test
    void shouldAllowAgentRunAfterWorkspaceVerification() {
        Run run = new Run("tenant-agent-validation-ok", "worker-user", "Agent 修改验证通过", "输入",
                BigDecimal.TEN, "demo-model", "prompt-agent", "policy-v1",
                null, "workspace.write,workspace.read", true, 3);
        run.addStep(succeededStep(1, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"开始修改\",\"toolCalls\":[]}"));
        run.addStep(succeededStep(2, org.mingharness.runtime.domain.StepType.TOOL,
                "workspace.write", "{\"path\":\"src/App.java\"}"));
        run.addStep(succeededStep(3, org.mingharness.runtime.domain.StepType.TOOL,
                "workspace.read", "{\"path\":\"src/App.java\",\"sha256\":\"abc\"}"));
        run.addStep(succeededStep(4, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"修改已核验\",\"toolCalls\":[]}"));
        run.start();
        runRepository.saveAndFlush(run);

        assertTrue(executionStateService.claim(run.getId(), run.getTenantId(), "validation-worker-ok",
                Instant.now().plusSeconds(30)).isPresent());
        assertTrue(executionStateService.finishSuccess(run.getId(), run.getTenantId(), "validation-worker-ok"));
        assertEquals(RunStatus.SUCCEEDED, runRepository.findById(run.getId()).orElseThrow().getStatus());
    }

    @Test
    void shouldNotFinishWorkerAgentAfterRejectedToolWithoutFollowUpModel() {
        Run run = new Run("tenant-agent-rejected-retry", "worker-user", "审批拒绝后重试保护", "输入",
                BigDecimal.TEN, "demo-model", "prompt-agent", "policy-v1",
                null, null, true, 1);
        run.addStep(succeededStep(1, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"\",\"toolCalls\":[{\"id\":\"call-1\",\"name\":\"demo.approval\",\"arguments\":\"\\\"高风险操作\\\"\"}]}"));
        org.mingharness.runtime.domain.Step rejected = new org.mingharness.runtime.domain.Step(
                2, org.mingharness.runtime.domain.StepType.TOOL, "demo.approval", "高风险操作");
        rejected.requestApproval();
        rejected.reject("需要先补充测试", "人工审批已拒绝该工具调用。原因：需要先补充测试");
        run.addStep(rejected);
        run.start();
        runRepository.saveAndFlush(run);

        assertTrue(executionStateService.claim(run.getId(), run.getTenantId(), "rejected-worker",
                Instant.now().plusSeconds(30)).isPresent());
        assertFalse(executionStateService.finishSuccess(run.getId(), run.getTenantId(), "rejected-worker"));

        Run failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertTrue(failed.getError().contains("人工拒绝"));
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .anyMatch(event -> "AGENT_FINAL_MODEL_REQUIRED".equals(event.getEventType())));
    }

    @Test
    void shouldNotTreatUnavailableGitAsWorkspaceVerification() {
        Run run = new Run("tenant-agent-validation-git", "worker-user", "Git 审阅降级", "输入",
                BigDecimal.TEN, "demo-model", "prompt-agent", "policy-v1",
                null, "workspace.write,workspace.read", true, 3);
        run.addStep(succeededStep(1, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"开始修改\",\"toolCalls\":[]}"));
        run.addStep(succeededStep(2, org.mingharness.runtime.domain.StepType.TOOL,
                "workspace.write", "{\"path\":\"src/App.java\"}"));
        run.addStep(succeededStep(3, org.mingharness.runtime.domain.StepType.TOOL,
                "workspace.git.diff", "{\"ok\":false,\"available\":false,"
                        + "\"recoverable\":true,\"verificationEligible\":false}"));
        run.addStep(succeededStep(4, org.mingharness.runtime.domain.StepType.MODEL,
                "model.complete", "{\"content\":\"Git 不可用\",\"toolCalls\":[]}"));
        run.start();
        runRepository.saveAndFlush(run);

        assertTrue(executionStateService.claim(run.getId(), run.getTenantId(), "validation-worker-git",
                Instant.now().plusSeconds(30)).isPresent());
        assertFalse(executionStateService.finishSuccess(run.getId(), run.getTenantId(), "validation-worker-git"));

        Run failed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertTrue(failed.getError().contains("重新读取文件"));
    }

    private org.mingharness.runtime.domain.Step succeededStep(int sequence,
                                                                org.mingharness.runtime.domain.StepType type,
                                                                String name, String output) {
        org.mingharness.runtime.domain.Step step =
                new org.mingharness.runtime.domain.Step(sequence, type, name, "input");
        step.start();
        step.succeed(output);
        return step;
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

        @Bean
        HarnessTool restrictedAgentTool() {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.agent-restricted", "需要额外权限的 Agent 工具",
                            true, "LOW", false, Map.of("type", "object"),
                            java.util.Set.of("test.agent.read"), 30_000, 1,
                            "DENY_EXTERNAL", Map.of("type", "object"));
                }

                @Override
                public String execute(String input) {
                    return "不应执行";
                }
            };
        }
    }

    @TestConfiguration
    static class AgentWorkerModelConfiguration {

        @Bean
        @org.springframework.context.annotation.Primary
        ModelGateway agentWorkerModelGateway() {
            DemoModelGateway fallback = new DemoModelGateway();
            return new ModelGateway() {
                @Override
                public ModelResponse complete(ModelRequest request) {
                    if (request.tools().isEmpty()) return fallback.complete(request);
                    if (request.input().contains("权限拒绝")) {
                        return new ModelResponse("", "agent-worker-test", request.promptVersion(), 5, 4,
                                java.math.BigDecimal.ZERO,
                                java.util.List.of(new ModelToolCall(
                                        "call-worker-agent-restricted", "test.agent-restricted", "{}")));
                    }
                    if (request.input().contains("工具 demo.echo 返回")) {
                        return new ModelResponse("Worker Agent 最终结果", "agent-worker-test",
                                request.promptVersion(), 5, 3);
                    }
                    return new ModelResponse("", "agent-worker-test", request.promptVersion(), 5, 4,
                            java.math.BigDecimal.ZERO,
                            java.util.List.of(new ModelToolCall(
                                    "call-worker-agent-1", "demo.echo", "\"worker agent input\"")));
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
