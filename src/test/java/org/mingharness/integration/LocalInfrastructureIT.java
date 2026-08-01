package org.mingharness.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.messaging.RabbitQueueDepthMonitor;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.application.TenantPolicyService;
import org.mingharness.runtime.api.TenantPolicyRequest;
import org.mingharness.runtime.api.TenantPolicyView;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void shouldReadRabbitQueueDepthForBackpressure() {
        assertTrue(queueDepthMonitor.availableCapacity().isPresent());
        assertTrue(meterRegistry.get("harness.rabbit.queue.depth").gauge().value() >= 0);
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
}
