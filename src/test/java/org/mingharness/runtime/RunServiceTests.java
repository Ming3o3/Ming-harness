package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunPage;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.HarnessTool;
import org.mingharness.tool.RetryableToolException;
import org.mingharness.tool.ToolDefinition;
import org.mingharness.model.ModelGateway;
import org.mingharness.model.ModelMessage;
import org.mingharness.model.ModelRequest;
import org.mingharness.model.ModelResponse;
import org.mingharness.model.ModelToolCall;
import org.mingharness.model.DemoModelGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import({RunServiceTests.FailureToolConfiguration.class, RunServiceTests.AgentModelConfiguration.class})
class RunServiceTests {

    @Autowired
    private RunService runService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private AgentModelToolsState agentModelToolsState;

    @BeforeEach
    void cleanDatabase() {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        agentModelToolsState.reset();
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
        var traceEvent = auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).get(0);
        assertNotNull(firstResult.run().traceId());
        assertEquals(firstResult.run().traceId(), traceEvent.getTraceId());
        assertEquals("tenant-demo", traceEvent.getTenantId());
    }

    @Test
    void shouldExecuteAgentModelToolModelLoopAndPersistDynamicSteps() {
        CreateRunRequest request = new CreateRunRequest(
                "tenant-demo", "user-demo", "代码 Agent 任务", "请读取并分析项目入口",
                null, null, "prompt-agent", "policy-v1", BigDecimal.TEN,
                null, null, true, 3);

        RunSummary created = runService.create(request);
        assertEquals(1, created.stepCount());
        assertTrue(created.agentMode());
        assertEquals(3, created.maxTurns());

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.SUCCEEDED, result.run().status(), result.run().error());
        assertEquals(3, result.steps().size());
        assertEquals(StepStatus.SUCCEEDED, result.steps().get(0).status());
        assertEquals("demo.echo", result.steps().get(1).name());
        assertEquals(StepStatus.SUCCEEDED, result.steps().get(1).status());
        assertEquals(StepStatus.SUCCEEDED, result.steps().get(2).status());
        assertEquals("Agent 最终结果", result.run().output());
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .anyMatch(event -> "AGENT_TOOL_CALL_REQUESTED".equals(event.getEventType())));
    }

    @Test
    void shouldOnlyExposeAgentToolsGrantedByRunPermissions() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-demo", "user-demo", "按权限筛选工具", "请完成一次 Agent 任务",
                null, null, "prompt-agent", "policy-v1", BigDecimal.TEN,
                null, "some.other.permission", true, 3));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.SUCCEEDED, result.run().status(), result.run().error());
        assertFalse(agentModelToolsState.firstToolNames().contains("test.secured"));
        assertTrue(agentModelToolsState.firstToolNames().contains("demo.echo"));
        assertTrue(agentModelToolsState.firstSystemPrompt().contains("sha256"));
        assertTrue(agentModelToolsState.firstSystemPrompt().contains("workspace.git.diff"));
        assertTrue(agentModelToolsState.firstSystemPrompt().contains("available=false"));
        assertTrue(agentModelToolsState.firstSystemPrompt().contains("不要声称未运行的测试"));
    }

    @Test
    void shouldFailAgentWhenMaxTurnsIsReached() {
        CreateRunRequest request = new CreateRunRequest(
                "tenant-demo", "user-demo", "Agent 最大轮数", "达到最多轮数后仍请求工具",
                null, null, "prompt-agent", "policy-v1", BigDecimal.TEN,
                null, null, true, 1);

        RunSummary created = runService.create(request);
        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.FAILED, result.run().status());
        assertTrue(result.run().error().contains("最大轮数"));
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .anyMatch(event -> "AGENT_MAX_TURNS_EXCEEDED".equals(event.getEventType())));
    }

    @Test
    void shouldStopAgentWhenItRepeatsThePreviousToolCall() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-demo", "user-demo", "重复工具调用", "重复工具调用测试",
                null, null, "prompt-agent", "policy-v1", BigDecimal.TEN,
                null, null, true, 1_000));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.FAILED, result.run().status());
        assertTrue(result.run().error().contains("重复请求"));
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .anyMatch(event -> "AGENT_DUPLICATE_TOOL_CALL".equals(event.getEventType())));
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
    void shouldPageRunsAndFilterByStatusWithoutLoadingAllRows() {
        runService.create(request("demo.echo", "分页任务一"));
        runService.create(request("demo.echo", "分页任务二"));

        RunPage firstPage = runService.listPage("tenant-demo", 0, 1, null);
        assertEquals(1, firstPage.items().size());
        assertEquals(2, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertTrue(firstPage.hasNext());

        RunPage queuedPage = runService.listPage("tenant-demo", 0, 20, RunStatus.QUEUED);
        assertEquals(2, queuedPage.totalElements());
        assertTrue(queuedPage.items().stream().allMatch(item -> item.status() == RunStatus.QUEUED));
    }

    @Test
    void shouldRejectInvalidPageSize() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> runService.listPage("tenant-demo", 0, 101, null));

        assertEquals("INVALID_PAGE_SIZE", exception.getCode());
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
    void shouldNotReuseIdempotencyKeyAcrossDifferentPermissionSnapshots() {
        runService.create(request("demo.echo", "相同输入").withIdempotencyKey("request-permission")
                .withPermissions("orders.read"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> runService.create(request("demo.echo", "相同输入")
                        .withIdempotencyKey("request-permission"))
        );

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
    }

    @Test
    void shouldRejectSensitiveIdempotencyKeyBeforePersistingIt() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> runService.create(request("demo.echo", "安全输入")
                        .withIdempotencyKey("api_key=do-not-store")));

        assertEquals("SENSITIVE_IDEMPOTENCY_KEY_REJECTED", exception.getCode());
    }

    @Test
    void shouldPauseForApprovalAndResumeAfterApproval() {
        RunSummary created = runService.create(request("demo.approval", "执行高风险演示操作"));

        RunDetail waiting = runService.start(created.id(), "tenant-demo");
        assertEquals(RunStatus.WAITING_APPROVAL, waiting.run().status());
        assertEquals(StepStatus.WAITING_APPROVAL, waiting.steps().get(1).status());

        RunDetail completed = runService.approve(created.id(), "tenant-demo", "approver-1");
        assertEquals(RunStatus.SUCCEEDED, completed.run().status());
        assertEquals(StepStatus.SUCCEEDED, completed.steps().get(1).status());
        assertEquals(1, completed.steps().get(1).attempt());
        assertEquals(1, auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "APPROVAL_APPROVED".equals(event.getEventType()))
                .count());
        var approvalEvent = auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "APPROVAL_APPROVED".equals(event.getEventType()))
                .findFirst().orElseThrow();
        assertEquals("approver-1", approvalEvent.getActorId());
        org.junit.jupiter.api.Assertions.assertTrue(approvalEvent.getMetadata().contains("demo.approval"));
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

    @Test
    void shouldAutoRetryExplicitRetryableReadOnlyTool() {
        RunSummary created = runService.create(request("test.auto-flaky", "自动重试"));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.SUCCEEDED, result.run().status());
        assertEquals(2, result.steps().get(1).attempt());
        assertEquals(1, auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "STEP_RETRY_SCHEDULED".equals(event.getEventType()))
                .count());
    }

    @Test
    void shouldNeverAutoRetrySideEffectTool() {
        RunSummary created = runService.create(request("test.side-effect-flaky", "副作用重试保护"));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.FAILED, result.run().status());
        assertEquals(1, result.steps().get(1).attempt());
        assertEquals(0, auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "STEP_RETRY_SCHEDULED".equals(event.getEventType()))
                .count());
    }

    @Test
    void shouldFailRunWhenModelCostExceedsBudget() {
        CreateRunRequest lowBudget = new CreateRunRequest(
                "tenant-demo", "user-demo", "预算校验", "超预算", "demo.echo",
                null, "prompt-v1", "policy-v1", BigDecimal.valueOf(0.000001));
        RunSummary created = runService.create(lowBudget);

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.FAILED, result.run().status());
        assertEquals("模型调用成本超过 Run 预算", result.run().error());
        assertEquals(StepStatus.FAILED, result.steps().get(0).status());
        assertEquals(1, auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .filter(event -> "BUDGET_EXCEEDED".equals(event.getEventType()))
                .count());
    }

    @Test
    void shouldDenyToolWhenPermissionIsMissing() {
        RunSummary created = runService.create(request("test.secured", "{\"query\":\"读取订单\"}"));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.FAILED, result.run().status());
        assertEquals(StepStatus.FAILED, result.steps().get(1).status());
        assertEquals("缺少工具所需权限: orders.read", result.run().error());
    }

    @Test
    void shouldAllowToolWhenPermissionIsSnapshotted() {
        RunSummary created = runService.create(request("test.secured", "{\"query\":\"读取订单\"}")
                .withPermissions("orders.read"));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.SUCCEEDED, result.run().status());
        assertEquals("已读取订单: {\"query\":\"读取订单\"}", result.run().output());
    }

    @Test
    void shouldSanitizeRunInputOutputAndApprovalAudit() {
        String secret = "approval-secret-123";
        RunSummary created = runService.create(request("demo.approval",
                "authorization: Bearer " + secret));

        assertFalse(created.input().contains(secret));
        RunDetail waiting = runService.start(created.id(), "tenant-demo");
        RunDetail completed = runService.approve(created.id(), "tenant-demo", "approver-1");

        assertFalse(completed.run().output().contains(secret));
        assertTrue(auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(created.id()).stream()
                .allMatch(event -> !event.getMessage().contains(secret)
                        && (event.getMetadata() == null || !event.getMetadata().contains(secret))));
        assertEquals(RunStatus.SUCCEEDED, completed.run().status());
    }

    @Test
    void shouldTimeoutSlowTool() {
        RunSummary created = runService.create(request("test.slow", "慢任务"));

        RunDetail result = runService.start(created.id(), "tenant-demo");

        assertEquals(RunStatus.TIMED_OUT, result.run().status());
        assertEquals(StepStatus.TIMED_OUT, result.steps().get(1).status());
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

        @Bean
        HarnessTool autoFlakyTool() {
            AtomicInteger attempts = new AtomicInteger();
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.auto-flaky", "可自动重试的只读工具", true, "LOW", false,
                            Map.of(), Set.of(), 1_000, 2, "DENY_EXTERNAL", Map.of());
                }

                @Override
                public String execute(String input) {
                    if (attempts.getAndIncrement() == 0) {
                        throw new RetryableToolException("外部依赖暂时不可用");
                    }
                    return "自动重试成功: " + input;
                }
            };
        }

        @Bean
        HarnessTool sideEffectFlakyTool() {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.side-effect-flaky", "禁止自动重试的副作用工具",
                            false, "LOW", false, Map.of(), Set.of(), 1_000, 3,
                            "DENY_EXTERNAL", Map.of());
                }

                @Override
                public String execute(String input) {
                    throw new RetryableToolException("外部依赖暂时不可用");
                }
            };
        }

        @Bean
        HarnessTool securedTool() {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.secured", "需要订单读取权限的测试工具", true, "LOW", false,
                            Map.of("type", "object"), Set.of("orders.read"), 1_000, 1,
                            "DENY_EXTERNAL", Map.of());
                }

                @Override
                public String execute(String input) {
                    return "已读取订单: " + input;
                }
            };
        }

        @Bean
        HarnessTool slowTool() {
            return new HarnessTool() {
                @Override
                public ToolDefinition definition() {
                    return new ToolDefinition("test.slow", "用于验证超时边界的工具", true, "LOW", false,
                            Map.of(), Set.of(), 20, 1, "DENY_EXTERNAL", Map.of());
                }

                @Override
                public String execute(String input) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return input;
                }
            };
        }
    }

    @TestConfiguration
    static class AgentModelConfiguration {

        @Bean
        AgentModelToolsState agentModelToolsState() {
            return new AgentModelToolsState();
        }

        @Bean
        @org.springframework.context.annotation.Primary
        ModelGateway agentModelGateway(AgentModelToolsState state) {
            DemoModelGateway fallback = new DemoModelGateway();
            return new ModelGateway() {
                @Override
                public ModelResponse complete(ModelRequest request) {
                    state.record(request);
                    if (request.tools().isEmpty()) return fallback.complete(request);
                    if (request.input().contains("重复工具调用")) {
                        return new ModelResponse("", "agent-test", request.promptVersion(), 5, 4,
                                java.math.BigDecimal.ZERO,
                                java.util.List.of(new ModelToolCall(
                                        "call-agent-repeat", "demo.echo", "\"重复\"")));
                    }
                    if (request.input().contains("达到最多轮数")) {
                        return new ModelResponse("", "agent-test", request.promptVersion(), 5, 4,
                                java.math.BigDecimal.ZERO,
                                java.util.List.of(new ModelToolCall(
                                        "call-agent-loop", "demo.echo", "\"loop\"")));
                    }
                    if (request.input().contains("工具 demo.echo 返回")) {
                        return new ModelResponse("Agent 最终结果", "agent-test", request.promptVersion(), 5, 3);
                    }
                    return new ModelResponse("", "agent-test", request.promptVersion(), 5, 4,
                            java.math.BigDecimal.ZERO,
                            java.util.List.of(new ModelToolCall(
                                    "call-agent-1", "demo.echo", "\"agent input\"")));
                }
            };
        }
    }

    static class AgentModelToolsState {
        private final List<List<String>> calls = Collections.synchronizedList(new ArrayList<>());
        private final List<String> systemPrompts = Collections.synchronizedList(new ArrayList<>());

        void record(ModelRequest request) {
            calls.add(request.tools().stream().map(tool -> tool.name()).toList());
            request.messages().stream()
                    .filter(message -> "system".equals(message.role()))
                    .map(ModelMessage::content)
                    .findFirst()
                    .ifPresent(systemPrompts::add);
        }

        List<String> firstToolNames() {
            synchronized (calls) {
                return calls.isEmpty() ? List.of() : List.copyOf(calls.get(0));
            }
        }

        String firstSystemPrompt() {
            synchronized (systemPrompts) {
                return systemPrompts.isEmpty() ? "" : systemPrompts.get(0);
            }
        }

        void reset() {
            calls.clear();
            systemPrompts.clear();
        }
    }
}
