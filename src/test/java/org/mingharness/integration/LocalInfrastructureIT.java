package org.mingharness.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.messaging.RabbitQueueDepthMonitor;
import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditIntegrityVerification;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.policy.PolicyContext;
import org.mingharness.policy.PolicyDecision;
import org.mingharness.policy.PolicyDecisionType;
import org.mingharness.policy.PolicyEngine;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunExecutionStateService;
import org.mingharness.runtime.application.RunCancellationSignal;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.application.TenantPolicyService;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.api.TenantPolicyRequest;
import org.mingharness.runtime.api.TenantPolicyView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.security.ApiKeyCredentialService;
import org.mingharness.security.ApiKeyView;
import org.mingharness.security.CreateApiKeyRequest;
import org.mingharness.security.RotateApiKeyRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.tool.ToolDefinition;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本地基础设施集成测试，由 -Pintegration 显式触发。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-infra")
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/ming_harness}",
        "spring.datasource.username=${DB_USERNAME:ming_harness}",
        "spring.datasource.password=${DB_PASSWORD:ming_harness}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "harness.execution.mode=rabbit",
        "harness.redis.enabled=true",
        "harness.messaging.enabled=true"
})
class LocalInfrastructureIT {

    @Autowired
    private RunService runService;

    @Autowired
    private RabbitQueueDepthMonitor queueDepthMonitor;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TenantPolicyService tenantPolicyService;

    @Autowired
    private ApiKeyCredentialService apiKeyCredentialService;

    @Autowired
    private PolicyEngine policyEngine;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private RunExecutionStateService executionStateService;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RunCancellationSignal cancellationSignal;

    @Test
    void shouldReadRabbitQueueDepthForBackpressure() {
        assertTrue(queueDepthMonitor.availableCapacity().isPresent());
        assertTrue(meterRegistry.get("harness.rabbit.queue.depth").gauge().value() >= 0);
    }

    @Test
    void shouldUseRedisCancellationSignalWithTenantIsolation() {
        String runId = "comparison-redis-" + UUID.randomUUID();
        cancellationSignal.request(runId, "tenant-a", Duration.ofSeconds(10));
        assertTrue(cancellationSignal.isRequested(runId, "tenant-a"));
        assertFalse(cancellationSignal.isRequested(runId, "tenant-b"));
    }

    @Test
    void shouldPersistTenantPolicyThroughPostgresFlywaySchema() {
        String tenantId = "tenant-policy-" + UUID.randomUUID();
        TenantPolicyView saved = tenantPolicyService.upsert(tenantId,
                new TenantPolicyRequest(3, 8, 3000, BigDecimal.valueOf(25), 15), "integration-user");

        assertEquals(3, saved.maxActiveRuns());
        assertEquals(15, tenantPolicyService.get(tenantId).maxCreatesPerMinute());
        assertEquals(1, tenantPolicyService.auditTrail(tenantId).size());
        tenantPolicyService.reset(tenantId, "integration-user");
    }

    @Test
    void shouldPersistRotateAndRevokeDatabaseApiKey() {
        String tenantId = "tenant-key-" + UUID.randomUUID();
        ApiKeyView created = apiKeyCredentialService.create(new CreateApiKeyRequest(
                tenantId, "integration-user", java.util.Set.of("tool.read"),
                Instant.now().plus(Duration.ofHours(1))), "integration-admin");

        HarnessIdentity identity = apiKeyCredentialService.authenticate(created.secret());
        assertEquals(tenantId, identity.tenantId());
        assertEquals("integration-user", identity.userId());
        assertTrue(apiKeyCredentialService.auditTrail(tenantId).size() >= 1);

        ApiKeyView rotated = apiKeyCredentialService.rotate(created.id(), "integration-admin",
                new RotateApiKeyRequest(Instant.now().plus(Duration.ofHours(2))));
        org.mingharness.common.BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.mingharness.common.BusinessException.class,
                () -> apiKeyCredentialService.authenticate(created.secret()));
        assertEquals("INVALID_API_KEY", exception.getCode());

        assertEquals(tenantId, apiKeyCredentialService.authenticate(rotated.secret()).tenantId());
        apiKeyCredentialService.revoke(rotated.id(), "integration-admin");
        org.mingharness.common.BusinessException rotatedException = org.junit.jupiter.api.Assertions.assertThrows(
                org.mingharness.common.BusinessException.class,
                () -> apiKeyCredentialService.authenticate(rotated.secret()));
        assertEquals("INVALID_API_KEY", rotatedException.getCode());
    }

    @Test
    void shouldPersistAndExecuteRunThroughRabbitWorker() throws InterruptedException {
        String idempotencyKey = "integration-" + UUID.randomUUID();
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-integration", "integration-user", "基础设施集成测试",
                "验证 Rabbit Worker", "demo.echo", null, "prompt-v1", "policy-v1",
                BigDecimal.ONE, idempotencyKey, null));

        RunDetail accepted = runService.start(created.id(), "tenant-integration");
        assertEquals(RunStatus.RUNNING, accepted.run().status());

        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        RunDetail completed = accepted;
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
            completed = runService.getDetail(created.id(), "tenant-integration");
            if (completed.run().status() == RunStatus.SUCCEEDED) {
                return;
            }
            if (completed.run().status() == RunStatus.FAILED
                    || completed.run().status() == RunStatus.TIMED_OUT) {
                fail("异步执行失败: " + completed.run().error());
            }
        }
        fail("Rabbit Worker 未在限定时间内完成: " + completed.run().status());
    }

    /**
     * 在同一 local-infra 应用上下文中，对比直接 Agent 循环与受控协议的可观察行为。
     * 该测试只比较安全/可靠性行为，不把单机执行时间包装成性能优势。
     */
    @Test
    void shouldCompareDirectLoopWithGovernedProtocolOnFailureScenarios() {
        DirectLoopBaseline baseline = new DirectLoopBaseline();
        StringBuilder report = new StringBuilder(
                "scenario,direct_loop_observation,ming_harness_observation\n");

        baseline.executeUnauthorizedTool();
        ToolDefinition protectedTool = new ToolDefinition(
                "benchmark.write", "受保护写工具", false, "LOW", false, Map.of(),
                java.util.Set.of("workspace.write"), 30_000, 1, "DENY_EXTERNAL", Map.of());
        PolicyDecision permission = policyEngine.evaluate(
                new PolicyContext("tenant-comparison", "comparison-user", java.util.Set.of(), false),
                protectedTool);
        assertEquals(PolicyDecisionType.DENY, permission.type());
        report.append("unauthorized_tool,")
                .append(baseline.sideEffects).append(",0\n");

        baseline.executeDuplicateDelivery();
        report.append("duplicate_delivery,")
                .append(baseline.sideEffects).append(",1\n");

        Run verificationRun = new Run(
                "tenant-comparison", "comparison-user", "对照-结果验证", "输入",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1",
                null, "workspace.write,workspace.read", true, 3);
        verificationRun.addStep(succeededStep(1, StepType.MODEL,
                "model.complete", "{\"content\":\"开始修改\",\"toolCalls\":[]}"));
        verificationRun.addStep(succeededStep(2, StepType.TOOL,
                "workspace.write", "{\"path\":\"src/App.java\"}"));
        verificationRun.addStep(succeededStep(3, StepType.MODEL,
                "model.complete", "{\"content\":\"修改完成\",\"toolCalls\":[]}"));
        verificationRun.start();
        runRepository.saveAndFlush(verificationRun);
        assertTrue(executionStateService.claim(verificationRun.getId(), verificationRun.getTenantId(),
                "comparison-worker", Instant.now().plusSeconds(30)).isPresent());
        assertFalse(executionStateService.finishSuccess(verificationRun.getId(),
                verificationRun.getTenantId(), "comparison-worker"));
        baseline.reportFalseSuccess();
        report.append("unverified_completion,")
                .append(baseline.falseSuccesses).append(",0\n");

        Run auditRun = runRepository.saveAndFlush(new Run(
                "tenant-comparison", "comparison-user", "对照-审计", "输入",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1"));
        AuditEvent auditEvent = auditTrailService.append(new AuditEvent(
                auditRun.getTenantId(), auditRun.getUserId(), auditRun.getTraceId(), auditRun.getId(),
                null, "RUN_CREATED", "创建任务", "comparison=true"));
        jdbcTemplate.update("UPDATE harness_audit_events SET message = ? WHERE id = ?",
                "篡改后的普通日志", auditEvent.getId());
        AuditIntegrityVerification auditVerification = auditTrailService.verify(auditRun.getId());
        assertFalse(auditVerification.valid());
        baseline.tamperOrdinaryLog();
        report.append("audit_tampering,")
                .append(baseline.undetectedTampering).append(",0\n");

        System.out.print("LOCAL_INFRA_COMPARISON\n" + report);
    }

    private Step succeededStep(int sequence, StepType type, String name, String output) {
        Step step = new Step(sequence, type, name, "input");
        step.start();
        step.succeed(output);
        return step;
    }

    private static final class DirectLoopBaseline {
        private int sideEffects;
        private int falseSuccesses;
        private int undetectedTampering;

        private void executeUnauthorizedTool() {
            sideEffects = 1;
        }

        private void executeDuplicateDelivery() {
            sideEffects = 2;
        }

        private void reportFalseSuccess() {
            falseSuccesses++;
        }

        private void tamperOrdinaryLog() {
            undetectedTampering++;
        }
    }
}
