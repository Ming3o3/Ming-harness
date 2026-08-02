package org.mingharness.runtime.application;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManagerFactory;
import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.observability.HarnessMetrics;
import org.mingharness.conversation.ConversationMessageWriter;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.time.Instant;

/** 扫描 Worker 中断后遗留的 RUNNING 任务，安全转为超时并等待人工重试。 */
@Component
public class RunRecoveryService implements SmartLifecycle {

    private static final int RECOVERY_BATCH_SIZE = 100;

    private final RunRepository runRepository;
    private final AuditTrailService auditTrailService;
    private final RuntimeLimits runtimeLimits;
    private final HarnessMetrics metrics;
    private final ConversationMessageWriter conversationMessageWriter;
    private final EntityManagerFactory entityManagerFactory;
    /** 防止测试或应用关闭后，调度线程继续访问已销毁的数据源。 */
    private volatile boolean shuttingDown;

    public RunRecoveryService(RunRepository runRepository,
                              AuditTrailService auditTrailService,
                              RuntimeLimits runtimeLimits,
                              HarnessMetrics metrics,
                              ConversationMessageWriter conversationMessageWriter,
                              EntityManagerFactory entityManagerFactory) {
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.runtimeLimits = runtimeLimits;
        this.metrics = metrics;
        this.conversationMessageWriter = conversationMessageWriter;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Scheduled(
            fixedDelayString = "${harness.runtime.recovery-scan-ms:30000}",
            initialDelayString = "${harness.runtime.recovery-initial-delay-ms:30000}"
    )
    @Transactional
    public void recoverStaleRuns() {
        if (shuttingDown || !entityManagerFactory.isOpen()) return;
        try {
            Instant now = Instant.now();
            Instant threshold = now.minusMillis(runtimeLimits.recoveryTimeoutMs());
            PageRequest batch = PageRequest.of(0, RECOVERY_BATCH_SIZE);
            java.util.LinkedHashMap<String, Run> staleRuns = new java.util.LinkedHashMap<>();
            // 查询本身持有行锁；如果 Worker 正在提交，数据库会先等待并重新判断 WHERE 条件。
            runRepository.findStaleByLeaseForUpdate(RunStatus.RUNNING, now, batch)
                    .forEach(run -> staleRuns.put(run.getId(), run));
            runRepository.findStaleWithoutLeaseForUpdate(RunStatus.RUNNING, threshold, batch)
                    .forEach(run -> staleRuns.put(run.getId(), run));
            for (Run run : staleRuns.values()) {
                run.timeout("Worker 执行中断，任务已转为超时状态，请人工重试");
                metrics.runTimedOut();
                runRepository.save(run);
                conversationMessageWriter.updateForTerminalRun(run);
                auditTrailService.append(new AuditEvent(
                        run.getTenantId(), run.getUserId(), run.getTraceId(), run.getId(), null,
                        "RUN_RECOVERED_AS_TIMED_OUT", run.getError(), "recovery=stale-running"));
            }
        } catch (RuntimeException exception) {
            // 关闭事件与调度触发可能同时发生；关闭阶段的数据源异常不应污染日志或测试结果。
            if (!shuttingDown && entityManagerFactory.isOpen()) throw exception;
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        shuttingDown = true;
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
    }

    /** 先于数据源等低阶段基础设施停止，避免最后一轮调度访问已销毁的表。 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void start() {
        shuttingDown = false;
    }

    @Override
    public void stop() {
        shutdown();
    }

    @Override
    public void stop(Runnable callback) {
        shutdown();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return !shuttingDown;
    }
}
