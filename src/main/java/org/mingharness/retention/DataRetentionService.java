package org.mingharness.retention;

import org.mingharness.audit.AuditEventRepository;
import org.mingharness.config.DataRetentionProperties;
import org.mingharness.context.KnowledgeDocumentRepository;
import org.mingharness.context.MemoryEntryRepository;
import org.mingharness.evaluation.EvaluationReportRepository;
import org.mingharness.messaging.OutboxEventRepository;
import org.mingharness.messaging.OutboxStatus;
import org.mingharness.observability.HarnessMetrics;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 按保留策略清理已过期的业务数据。
 *
 * Run、Step 和其审计链作为一个整体删除；不能单独裁剪仍然可查询的 Run 审计事件，
 * 否则 HMAC 链会被人为截断。PENDING/PUBLISHING Outbox 从不由保留任务删除，避免丢失尚未确认投递的命令。
 */
@Service
public class DataRetentionService {

    private static final List<RunStatus> TERMINAL_RUN_STATUSES = List.of(
            RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.TIMED_OUT, RunStatus.CANCELLED);

    private final DataRetentionProperties properties;
    private final RunRepository runRepository;
    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final MemoryEntryRepository memoryEntryRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final EvaluationReportRepository evaluationReportRepository;
    private final HarnessMetrics metrics;

    public DataRetentionService(DataRetentionProperties properties,
                                RunRepository runRepository,
                                AuditEventRepository auditEventRepository,
                                OutboxEventRepository outboxEventRepository,
                                MemoryEntryRepository memoryEntryRepository,
                                KnowledgeDocumentRepository documentRepository,
                                EvaluationReportRepository evaluationReportRepository,
                                HarnessMetrics metrics) {
        this.properties = properties;
        this.runRepository = runRepository;
        this.auditEventRepository = auditEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.memoryEntryRepository = memoryEntryRepository;
        this.documentRepository = documentRepository;
        this.evaluationReportRepository = evaluationReportRepository;
        this.metrics = metrics;
    }

    /** 已启用时按固定周期执行；关闭时保留运维手动调用 cleanupNow 的能力。 */
    @Scheduled(
            fixedDelayString = "${harness.data-retention.cleanup-interval-ms:3600000}",
            initialDelayString = "${harness.data-retention.cleanup-initial-delay-ms:60000}"
    )
    @Transactional
    public void scheduledCleanup() {
        if (properties.enabled()) {
            cleanup();
        }
    }

    /** 供运维任务和测试显式调用；不受 enabled 开关限制。 */
    @Transactional
    public RetentionCleanupResult cleanupNow() {
        return cleanup();
    }

    private RetentionCleanupResult cleanup() {
        Instant now = Instant.now();
        Instant runCutoff = now.minus(properties.effectiveRunRetentionDays(), ChronoUnit.DAYS);
        int runsDeleted = 0;
        int auditEventsDeleted = 0;
        int stepsDeleted = 0;
        int outboxEventsDeleted = 0;

        // 每轮只处理有限数量的 Run，避免历史数据很多时长事务阻塞线上写入。
        List<Run> candidates = runRepository.findByStatusInAndFinishedAtBeforeOrderByFinishedAtAsc(
                TERMINAL_RUN_STATUSES, runCutoff, PageRequest.of(0, properties.batchSize()));
        for (Run run : candidates) {
            auditEventsDeleted += Math.toIntExact(auditEventRepository.deleteByRunId(run.getId()));
            outboxEventsDeleted += Math.toIntExact(outboxEventRepository.deleteByRunId(run.getId()));
            stepsDeleted += run.getSteps().size();
            runRepository.delete(run);
            runsDeleted++;
        }

        int memoriesDeleted = Math.toIntExact(memoryEntryRepository.deleteByExpiresAtLessThanEqual(now));
        memoriesDeleted += Math.toIntExact(memoryEntryRepository.deleteByDeletedAtBefore(
                now.minus(properties.memoryDays(), ChronoUnit.DAYS)));
        int documentsDeleted = Math.toIntExact(documentRepository.deleteByDeletedAtBefore(
                now.minus(properties.documentDays(), ChronoUnit.DAYS)));
        int evaluationReportsDeleted = Math.toIntExact(evaluationReportRepository.deleteByCreatedAtBefore(
                now.minus(properties.evaluationDays(), ChronoUnit.DAYS)));
        // 仅清理已经完成投递或已明确失败的历史 Outbox，PENDING 事件永远保留。
        outboxEventsDeleted += Math.toIntExact(outboxEventRepository.deleteByStatusInAndCreatedAtBefore(
                List.of(OutboxStatus.PUBLISHED, OutboxStatus.FAILED),
                now.minus(properties.outboxDays(), ChronoUnit.DAYS)));

        RetentionCleanupResult result = new RetentionCleanupResult(
                runsDeleted, auditEventsDeleted, stepsDeleted, memoriesDeleted, documentsDeleted,
                evaluationReportsDeleted, outboxEventsDeleted);
        metrics.retentionDeleted(result.totalDeleted());
        return result;
    }
}
