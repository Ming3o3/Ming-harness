package org.mingharness.integration;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunService;
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
