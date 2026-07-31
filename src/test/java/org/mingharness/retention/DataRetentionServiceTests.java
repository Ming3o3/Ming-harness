package org.mingharness.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.context.KnowledgeDocument;
import org.mingharness.context.KnowledgeDocumentRepository;
import org.mingharness.context.MemoryEntry;
import org.mingharness.context.MemoryEntryRepository;
import org.mingharness.evaluation.EvaluationReport;
import org.mingharness.evaluation.EvaluationReportRepository;
import org.mingharness.messaging.OutboxEvent;
import org.mingharness.messaging.OutboxEventRepository;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证保留任务会清理过期数据，并保留未结束 Run 和待投递 Outbox。 */
@SpringBootTest
class DataRetentionServiceTests {

    @Autowired
    private DataRetentionService retentionService;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private AuditTrailService auditTrailService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private MemoryEntryRepository memoryEntryRepository;
    @Autowired
    private KnowledgeDocumentRepository documentRepository;
    @Autowired
    private EvaluationReportRepository evaluationReportRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        memoryEntryRepository.deleteAll();
        documentRepository.deleteAll();
        evaluationReportRepository.deleteAll();
    }

    @Test
    void shouldDeleteExpiredDataAsAnIntegrityPreservingUnit() {
        Instant old = Instant.now().minusSeconds(2 * 24 * 60 * 60);

        MemoryEntry expiredMemory = memoryEntryRepository.save(new MemoryEntry(
                "tenant-retention", "operator", "preference", "过期偏好", null, old));
        MemoryEntry deletedMemory = memoryEntryRepository.save(new MemoryEntry(
                "tenant-retention", "operator", "preference", "已删除偏好", null, null));
        deletedMemory.markDeleted();
        memoryEntryRepository.save(deletedMemory);
        jdbcTemplate.update("UPDATE harness_context_memories SET deleted_at = ? WHERE id = ?",
                Timestamp.from(old), deletedMemory.getId());

        KnowledgeDocument deletedDocument = documentRepository.save(new KnowledgeDocument(
                "tenant-retention", "operator", "旧文档", "旧文档内容", "INTERNAL", ""));
        deletedDocument.markDeleted();
        documentRepository.save(deletedDocument);
        jdbcTemplate.update("UPDATE harness_context_documents SET deleted_at = ? WHERE id = ?",
                Timestamp.from(old), deletedDocument.getId());

        EvaluationReport report = evaluationReportRepository.save(new EvaluationReport(
                "tenant-retention", "旧评测", "demo-model", "prompt-v1", "policy-v1",
                1, 1, 0, BigDecimal.ONE, "旧评测详情"));
        jdbcTemplate.update("UPDATE harness_evaluation_reports SET created_at = ? WHERE id = ?",
                Timestamp.from(old), report.getId());

        Run oldRun = runRepository.save(new Run("tenant-retention", "operator", "旧 Run", "输入",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1"));
        oldRun.start();
        oldRun.succeed("输出");
        runRepository.save(oldRun);
        AuditEvent auditEvent = auditTrailService.append(new AuditEvent(
                oldRun.getTenantId(), oldRun.getUserId(), oldRun.getTraceId(), oldRun.getId(), null,
                "RUN_SUCCEEDED", "旧 Run 成功", "retention=test"));
        OutboxEvent publishedOutbox = outboxEventRepository.save(new OutboxEvent(
                oldRun.getId(), oldRun.getTenantId(), oldRun.getTraceId(), "START", "{}"));
        publishedOutbox.markPublished();
        outboxEventRepository.save(publishedOutbox);
        jdbcTemplate.update("UPDATE harness_runs SET finished_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(old), Timestamp.from(old), oldRun.getId());
        jdbcTemplate.update("UPDATE harness_outbox_events SET created_at = ?, published_at = ? WHERE id = ?",
                Timestamp.from(old), Timestamp.from(old), publishedOutbox.getId());

        Run activeRun = runRepository.save(new Run("tenant-retention", "operator", "活动 Run", "输入",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1"));
        OutboxEvent pendingOutbox = outboxEventRepository.save(new OutboxEvent(
                activeRun.getId(), activeRun.getTenantId(), activeRun.getTraceId(), "START", "{}"));

        RetentionCleanupResult result = retentionService.cleanupNow();

        assertEquals(1, result.runsDeleted());
        assertEquals(1, result.auditEventsDeleted());
        assertEquals(0, result.stepsDeleted());
        assertEquals(2, result.memoriesDeleted());
        assertEquals(1, result.documentsDeleted());
        assertEquals(1, result.evaluationReportsDeleted());
        assertEquals(1, result.outboxEventsDeleted());
        assertFalse(runRepository.findById(oldRun.getId()).isPresent());
        assertTrue(runRepository.findById(activeRun.getId()).isPresent());
        assertFalse(auditEventRepository.findById(auditEvent.getId()).isPresent());
        assertTrue(outboxEventRepository.findById(pendingOutbox.getId()).isPresent());
        assertFalse(memoryEntryRepository.findById(expiredMemory.getId()).isPresent());
    }
}
