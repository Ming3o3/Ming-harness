package org.mingharness.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证审计事件的 HMAC 链可以发现内容篡改和事件删除。 */
@SpringBootTest
class AuditTrailServiceTests {

    @Autowired
    private AuditTrailService auditTrailService;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldAppendAndVerifyAuditChain() {
        Run run = saveRun();

        AuditEvent first = auditTrailService.append(event(run, "RUN_CREATED", "创建任务"));
        AuditEvent second = auditTrailService.append(event(run, "RUN_STARTED", "启动任务"));

        assertEquals(1L, first.getIntegritySequence());
        assertEquals(2L, second.getIntegritySequence());
        AuditIntegrityVerification verification = auditTrailService.verify(run.getId());
        assertTrue(verification.valid());
        assertEquals(2, verification.checkedEventCount());
    }

    @Test
    void shouldDetectAuditContentTampering() {
        Run run = saveRun();
        AuditEvent event = auditTrailService.append(event(run, "RUN_CREATED", "创建任务"));

        jdbcTemplate.update("UPDATE harness_audit_events SET message = ? WHERE id = ?",
                "篡改后的内容", event.getId());

        AuditIntegrityVerification verification = auditTrailService.verify(run.getId());
        assertFalse(verification.valid());
        assertEquals("AUDIT_EVENT_HASH_MISMATCH", verification.failureCode());
    }

    @Test
    void shouldDetectAuditEventDeletion() {
        Run run = saveRun();
        auditTrailService.append(event(run, "RUN_CREATED", "创建任务"));
        AuditEvent last = auditTrailService.append(event(run, "RUN_STARTED", "启动任务"));
        jdbcTemplate.update("DELETE FROM harness_audit_events WHERE id = ?", last.getId());

        AuditIntegrityVerification verification = auditTrailService.verify(run.getId());
        assertFalse(verification.valid());
        assertEquals("AUDIT_EVENT_COUNT_MISMATCH", verification.failureCode());
    }

    @Test
    void shouldSanitizeAuditPayloadBeforeSigningIt() {
        Run run = saveRun();
        AuditEvent event = auditTrailService.append(new AuditEvent(
                run.getTenantId(), run.getUserId(), run.getTraceId(), run.getId(), null,
                "TOOL_FAILED", "authorization: Bearer do-not-store",
                "api_key=do-not-store"));

        assertFalse(event.getMessage().contains("do-not-store"));
        assertFalse(event.getMetadata().contains("do-not-store"));
        assertTrue(auditTrailService.verify(run.getId()).valid());
    }

    private Run saveRun() {
        return runRepository.save(new Run(
                "tenant-audit", "audit-user", "审计测试", "审计输入", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1"));
    }

    private AuditEvent event(Run run, String type, String message) {
        return new AuditEvent(run.getTenantId(), run.getUserId(), run.getTraceId(),
                run.getId(), null, type, message, "test=true");
    }
}
