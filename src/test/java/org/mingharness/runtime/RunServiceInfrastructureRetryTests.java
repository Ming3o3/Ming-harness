package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.application.RunExecutionLock;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.application.TransientInfrastructureException;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.messaging.RunExecutionMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证临时 Worker 基础设施故障会回滚本次执行并交给 Rabbit 重试。 */
@SpringBootTest
@Import(RunServiceInfrastructureRetryTests.FailingLeaseConfiguration.class)
class RunServiceInfrastructureRetryTests {

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
    void shouldNotPersistFailedRunWhenExecutionLeaseCannotRenew() {
        RunSummary created = runService.create(new CreateRunRequest(
                "tenant-retry", "user-retry", "基础设施重试", "模拟 Redis 续租失败",
                "demo.echo", null, null, null, BigDecimal.ONE));
        Run run = runRepository.findById(created.id()).orElseThrow();
        run.start();
        runRepository.save(run);

        assertThrows(TransientInfrastructureException.class, () -> runService.executeFromWorker(
                new RunExecutionMessage("event-retry", run.getId(), run.getTenantId(), run.getTraceId(),
                        "START", Instant.now())));

        Run restored = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.RUNNING, restored.getStatus());
        assertEquals(StepStatus.QUEUED, restored.getSteps().get(0).getStatus());
    }

    @TestConfiguration
    static class FailingLeaseConfiguration {

        @Bean
        @Primary
        RunExecutionLock failingLeaseLock() {
            return new RunExecutionLock() {
                @Override
                public Optional<LockToken> tryAcquire(String runId, Duration lease) {
                    return Optional.of(new LockToken("test:" + runId, "token"));
                }

                @Override
                public boolean renew(LockToken token, Duration lease) {
                    return false;
                }

                @Override
                public void release(LockToken token) {
                    // 测试锁不持有外部资源。
                }
            };
        }
    }
}
