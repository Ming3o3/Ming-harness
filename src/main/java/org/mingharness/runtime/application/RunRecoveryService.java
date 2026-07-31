package org.mingharness.runtime.application;

import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 扫描 Worker 中断后遗留的 RUNNING 任务，安全转为超时并等待人工重试。 */
@Component
public class RunRecoveryService {

    private final RunRepository runRepository;
    private final AuditTrailService auditTrailService;
    private final RuntimeLimits runtimeLimits;
    private final HarnessMetrics metrics;

    public RunRecoveryService(RunRepository runRepository,
                              AuditTrailService auditTrailService,
                              RuntimeLimits runtimeLimits,
                              HarnessMetrics metrics) {
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.runtimeLimits = runtimeLimits;
        this.metrics = metrics;
    }

    @Scheduled(
            fixedDelayString = "${harness.runtime.recovery-scan-ms:30000}",
            initialDelayString = "${harness.runtime.recovery-initial-delay-ms:30000}"
    )
    @Transactional
    public void recoverStaleRuns() {
        Instant threshold = Instant.now().minusMillis(runtimeLimits.recoveryTimeoutMs());
        java.util.LinkedHashMap<String, Run> staleRuns = new java.util.LinkedHashMap<>();
        runRepository.findTop100ByStatusAndLeaseUntilBefore(RunStatus.RUNNING, Instant.now())
                .forEach(run -> staleRuns.put(run.getId(), run));
        runRepository.findTop100ByStatusAndLeaseUntilIsNullAndUpdatedAtBefore(RunStatus.RUNNING, threshold)
                .forEach(run -> staleRuns.put(run.getId(), run));
        for (Run run : staleRuns.values()) {
            run.timeout("Worker 执行中断，任务已转为超时状态，请人工重试");
            metrics.runTimedOut();
            run.clearLease();
            runRepository.save(run);
            auditTrailService.append(new AuditEvent(
                    run.getTenantId(), run.getUserId(), run.getTraceId(), run.getId(), null,
                    "RUN_RECOVERED_AS_TIMED_OUT", run.getError(), "recovery=stale-running"));
        }
    }
}
