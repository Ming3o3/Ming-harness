package org.mingharness.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.context.ContextChunk;
import org.mingharness.context.ContextChunkRepository;
import org.mingharness.context.ContextParentWindow;
import org.mingharness.context.ContextParentWindowRepository;
import org.mingharness.context.KnowledgeDocument;
import org.mingharness.context.KnowledgeDocumentRepository;
import org.mingharness.context.MemoryEntry;
import org.mingharness.context.MemoryEntryRepository;
import org.mingharness.evaluation.ContextRetrievalEvaluationReport;
import org.mingharness.evaluation.ContextRetrievalEvaluationReportRepository;
import org.mingharness.evaluation.EvaluationReport;
import org.mingharness.evaluation.EvaluationReportRepository;
import org.mingharness.messaging.OutboxEvent;
import org.mingharness.messaging.OutboxEventRepository;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.TenantPolicyAudit;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.runtime.repository.TenantPolicyAuditRepository;
import org.mingharness.security.ApiKeyAudit;
import org.mingharness.security.ApiKeyAuditRepository;
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
    private ContextChunkRepository contextChunkRepository;
    @Autowired
    private ContextParentWindowRepository contextParentWindowRepository;
    @Autowired
    private EvaluationReportRepository evaluationReportRepository;
    @Autowired
    private ContextRetrievalEvaluationReportRepository retrievalEvaluationReportRepository;
    @Autowired
    private TenantPolicyAuditRepository tenantPolicyAuditRepository;
    @Autowired
    private ApiKeyAuditRepository apiKeyAuditRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        memoryEntryRepository.deleteAll();
        documentRepository.deleteAll();
        contextChunkRepository.deleteAll();
        contextParentWindowRepository.deleteAll();
        evaluationReportRepository.deleteAll();
        retrievalEvaluationReportRepository.deleteAll();
        tenantPolicyAuditRepository.deleteAll();
        apiKeyAuditRepository.deleteAll();
    }

    @Test
    void shouldDeleteExpiredDataAsAnIntegrityPreservingUnit() {
        Instant old = Instant.now().minusSeconds(2 * 24 * 60 * 60);

        MemoryEntry expiredMemory = memoryEntryRepository.save(new MemoryEntry(
                "tenant-retention", "operator", "preference", "过期偏好", null, old));
        ContextParentWindow expiredMemoryWindow = contextParentWindowRepository.save(new ContextParentWindow(
                "tenant-retention", "MEMORY", expiredMemory.getId(), 0, "过期偏好", "window-memory"));
        ContextChunk expiredMemoryChunk = contextChunkRepository.save(new ContextChunk(
                "tenant-retention", "MEMORY", expiredMemory.getId(), 0, "过期偏好", "hash-memory",
                "DETERMINISTIC", "deterministic-v1", expiredMemoryWindow.getId()));
        MemoryEntry deletedMemory = memoryEntryRepository.save(new MemoryEntry(
                "tenant-retention", "operator", "preference", "已删除偏好", null, null));
        deletedMemory.markDeleted();
        memoryEntryRepository.save(deletedMemory);
        jdbcTemplate.update("UPDATE harness_context_memories SET deleted_at = ? WHERE id = ?",
                Timestamp.from(old), deletedMemory.getId());

        KnowledgeDocument deletedDocument = documentRepository.save(new KnowledgeDocument(
                "tenant-retention", "operator", "旧文档", "旧文档内容", "INTERNAL", ""));
        ContextParentWindow deletedDocumentWindow = contextParentWindowRepository.save(new ContextParentWindow(
                "tenant-retention", "DOCUMENT", deletedDocument.getId(), 0, "旧文档内容", "window-document"));
        ContextChunk deletedDocumentChunk = contextChunkRepository.save(new ContextChunk(
                "tenant-retention", "DOCUMENT", deletedDocument.getId(), 0, "旧文档内容", "hash-document",
                "DETERMINISTIC", "deterministic-v1", deletedDocumentWindow.getId()));
        deletedDocument.markDeleted();
        documentRepository.save(deletedDocument);
        jdbcTemplate.update("UPDATE harness_context_documents SET deleted_at = ? WHERE id = ?",
                Timestamp.from(old), deletedDocument.getId());

        MemoryEntry retainedMemory = memoryEntryRepository.save(new MemoryEntry(
                "tenant-retention", "operator", "preference", "仍有效的偏好", null, null));
        ContextParentWindow staleWindow = contextParentWindowRepository.save(new ContextParentWindow(
                "tenant-retention", "MEMORY", retainedMemory.getId(), 0, "历史窗口", "window-stale"));
        staleWindow.markDeleted();
        contextParentWindowRepository.save(staleWindow);
        jdbcTemplate.update("UPDATE harness_context_parent_windows SET deleted_at = ? WHERE id = ?",
                Timestamp.from(old), staleWindow.getId());

        EvaluationReport report = evaluationReportRepository.save(new EvaluationReport(
                "tenant-retention", "旧评测", "demo-model", "prompt-v1", "policy-v1",
                1, 1, 0, BigDecimal.ONE, "旧评测详情"));
        jdbcTemplate.update("UPDATE harness_evaluation_reports SET created_at = ? WHERE id = ?",
                Timestamp.from(old), report.getId());

        ContextRetrievalEvaluationReport retrievalReport = retrievalEvaluationReportRepository.save(
                new ContextRetrievalEvaluationReport("tenant-retention", "旧检索评测", 5, 1, 1,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1, 1, BigDecimal.ONE, "case-1"));
        jdbcTemplate.update("UPDATE harness_context_retrieval_evaluation_reports SET created_at = ? WHERE id = ?",
                Timestamp.from(old), retrievalReport.getId());

        TenantPolicyAudit policyAudit = tenantPolicyAuditRepository.save(new TenantPolicyAudit(
                "tenant-retention", "operator", "TENANT_POLICY_UPDATED", "old policy"));
        jdbcTemplate.update("UPDATE harness_tenant_policy_audits SET created_at = ? WHERE id = ?",
                Timestamp.from(old), policyAudit.getId());

        ApiKeyAudit apiKeyAudit = apiKeyAuditRepository.save(new ApiKeyAudit(
                "key-retention", "tenant-retention", "operator", "API_KEY_REVOKED", "old key"));
        jdbcTemplate.update("UPDATE harness_api_key_audits SET created_at = ? WHERE id = ?",
                Timestamp.from(old), apiKeyAudit.getId());

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
        Instant publishNow = Instant.now();
        publishedOutbox.claim("retention-test-relay", publishNow, publishNow.plusSeconds(30));
        publishedOutbox.markPublished("retention-test-relay");
        outboxEventRepository.save(publishedOutbox);
        jdbcTemplate.update("UPDATE harness_runs SET finished_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(old), Timestamp.from(old), oldRun.getId());
        jdbcTemplate.update("UPDATE harness_outbox_events SET created_at = ?, published_at = ? WHERE id = ?",
                Timestamp.from(old), Timestamp.from(old), publishedOutbox.getId());

        Run activeRun = runRepository.save(new Run("tenant-retention", "operator", "活动 Run", "输入",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1"));
        OutboxEvent pendingOutbox = outboxEventRepository.save(new OutboxEvent(
                activeRun.getId(), activeRun.getTenantId(), activeRun.getTraceId(), "START", "{}"));
        OutboxEvent publishingOutbox = outboxEventRepository.save(new OutboxEvent(
                activeRun.getId(), activeRun.getTenantId(), activeRun.getTraceId(), "START", "{}"));
        Instant claimNow = Instant.now();
        publishingOutbox.claim("active-relay", claimNow, claimNow.plusSeconds(30));
        outboxEventRepository.save(publishingOutbox);

        RetentionCleanupResult result = retentionService.cleanupNow();

        assertEquals(1, result.runsDeleted());
        assertEquals(1, result.auditEventsDeleted());
        assertEquals(0, result.stepsDeleted());
        assertEquals(2, result.memoriesDeleted());
        assertEquals(1, result.documentsDeleted());
        assertEquals(2, result.chunksDeleted());
        assertEquals(3, result.parentWindowsDeleted());
        assertEquals(1, result.evaluationReportsDeleted());
        assertEquals(1, result.retrievalEvaluationReportsDeleted());
        assertEquals(1, result.outboxEventsDeleted());
        assertEquals(1, result.tenantPolicyAuditsDeleted());
        assertEquals(1, result.apiKeyAuditsDeleted());
        assertFalse(runRepository.findById(oldRun.getId()).isPresent());
        assertTrue(runRepository.findById(activeRun.getId()).isPresent());
        assertFalse(auditEventRepository.findById(auditEvent.getId()).isPresent());
        assertTrue(outboxEventRepository.findById(pendingOutbox.getId()).isPresent());
        assertTrue(outboxEventRepository.findById(publishingOutbox.getId()).isPresent());
        assertFalse(memoryEntryRepository.findById(expiredMemory.getId()).isPresent());
        assertFalse(contextChunkRepository.findById(expiredMemoryChunk.getId()).isPresent());
        assertFalse(contextChunkRepository.findById(deletedDocumentChunk.getId()).isPresent());
        assertFalse(contextParentWindowRepository.findById(expiredMemoryWindow.getId()).isPresent());
        assertFalse(contextParentWindowRepository.findById(deletedDocumentWindow.getId()).isPresent());
        assertFalse(contextParentWindowRepository.findById(staleWindow.getId()).isPresent());
        assertFalse(apiKeyAuditRepository.findById(apiKeyAudit.getId()).isPresent());
    }
}
