package org.mingharness.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.runtime.application.RunRecoveryService;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Worker 中断后的租约恢复和未过期任务保护。 */
@SpringBootTest
class RunRecoveryServiceTests {

    @Autowired
    private RunRecoveryService recoveryService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private AuditTrailService auditTrailService;

    @BeforeEach
    void cleanDatabase() {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldRecoverExpiredWorkerLeaseAndAppendAuditEvent() {
        Run run = new Run("tenant-recovery", "operator", "中断任务", "恢复测试",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        run.start();
        run.claim("worker-crashed", Instant.now().minusSeconds(5));
        runRepository.saveAndFlush(run);

        recoveryService.recoverStaleRuns();

        Run recovered = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(RunStatus.TIMED_OUT, recovered.getStatus());
        assertEquals(1, auditEventRepository.findByRunIdOrderByIntegritySequenceAsc(run.getId()).size());
        assertTrue(auditTrailService.verify(run.getId()).valid());
    }

    @Test
    void shouldKeepActiveWorkerLeaseRunning() {
        Run run = new Run("tenant-recovery-active", "operator", "活动任务", "恢复测试",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        run.start();
        run.claim("worker-active", Instant.now().plusSeconds(60));
        runRepository.saveAndFlush(run);

        recoveryService.recoverStaleRuns();

        assertEquals(RunStatus.RUNNING, runRepository.findById(run.getId()).orElseThrow().getStatus());
        assertEquals(0, auditEventRepository.findByRunIdOrderByIntegritySequenceAsc(run.getId()).size());
    }
}
