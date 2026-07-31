package org.mingharness.runtime.application;

import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Worker 执行期间的短事务状态边界。
 *
 * <p>模型和工具调用在该服务之外执行，只有状态变更、租约心跳和审计追加进入数据库事务，
 * 避免外部网络延迟长期占用连接和 Run 行锁。</p>
 */
@Service
public class RunExecutionStateService {

    private final RunRepository runRepository;
    private final AuditTrailService auditTrailService;
    private final SensitiveDataSanitizer sanitizer;

    public RunExecutionStateService(RunRepository runRepository,
                                    AuditTrailService auditTrailService,
                                    SensitiveDataSanitizer sanitizer) {
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.sanitizer = sanitizer;
    }

    /** 获取最新 Run 并建立 Worker 租约，事务提交后才开始外部调用。 */
    @Transactional
    public Optional<RunExecutionSnapshot> claim(String runId, String tenantId,
                                                String workerId, Instant leaseUntil) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() != RunStatus.RUNNING) {
            return Optional.empty();
        }
        run.claim(workerId, leaseUntil);
        runRepository.save(run);
        append(run, null, "WORKER_CLAIMED", "Worker 已获取执行租约");
        return Optional.of(snapshot(run));
    }

    /** 每个步骤开始前单独提交状态和审计，之后才调用模型或工具。 */
    @Transactional
    public Optional<StepExecutionSnapshot> startStep(String runId, String tenantId,
                                                      String workerId, String stepId,
                                                      Instant leaseUntil) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return Optional.empty();
        }
        Step step = findStep(run, stepId);
        if (step.getStatus() == StepStatus.SUCCEEDED) {
            return Optional.of(stepSnapshot(step));
        }
        if (step.getStatus() != StepStatus.QUEUED) {
            throw new IllegalStateException("步骤当前不能启动: " + step.getStatus());
        }
        run.heartbeat(workerId, leaseUntil);
        step.start();
        runRepository.save(run);
        append(run, step, "STEP_STARTED", "开始执行步骤: " + step.getName()
                + "（第 " + step.getAttempt() + " 次尝试）");
        return Optional.of(stepSnapshot(step));
    }

    /** Worker 在步骤边界刷新租约，事务极短且只读取并更新最新 Run。 */
    @Transactional
    public boolean heartbeat(String runId, String tenantId, String workerId, Instant leaseUntil) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        run.heartbeat(workerId, leaseUntil);
        runRepository.save(run);
        return true;
    }

    /** 检索来源数量属于审计信息，单独短事务写入且不携带原始上下文正文。 */
    @Transactional
    public void recordContextRetrieved(String runId, String tenantId, String workerId,
                                       String stepId, int evidenceCount) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return;
        }
        Step step = findStep(run, stepId);
        append(run, step, "CONTEXT_RETRIEVED", "检索到 " + Math.max(0, evidenceCount) + " 条授权来源");
        runRepository.save(run);
    }

    /** 策略要求审批时只改变状态，不在 Worker 事务外持有实体。 */
    @Transactional
    public boolean requestApproval(String runId, String tenantId, String workerId,
                                   String stepId, String reason) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        Step step = findStep(run, stepId);
        step.requestApproval();
        run.waitApproval();
        run.clearLease();
        runRepository.save(run);
        append(run, step, "APPROVAL_REQUESTED", sanitizer.sanitize(reason));
        return true;
    }

    /**
     * 持久化步骤成功结果；写入前再次读取取消状态和 Worker 所有权，防止旧 Worker 覆盖取消结果。
     */
    @Transactional
    public StepCompletionResult completeStep(String runId, String tenantId, String workerId,
                                             String stepId, String output, int inputTokens,
                                             int outputTokens, BigDecimal cost) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return StepCompletionResult.CANCELLED_OR_NOT_OWNER;
        }
        Step step = findStep(run, stepId);
        BigDecimal safeCost = cost == null ? BigDecimal.ZERO : cost;
        if (exceedsBudget(run, safeCost)) {
            step.fail("模型调用成本超过 Run 预算");
            append(run, step, "BUDGET_EXCEEDED", step.getError());
            run.fail(step.getError());
            append(run, null, "RUN_FAILED", run.getError());
            runRepository.save(run);
            return StepCompletionResult.BUDGET_EXCEEDED;
        }
        if (step.getStatus() != StepStatus.RUNNING) {
            return StepCompletionResult.CANCELLED_OR_NOT_OWNER;
        }
        step.succeed(sanitizer.sanitize(output), inputTokens, outputTokens, safeCost);
        append(run, step, "STEP_SUCCEEDED", "步骤执行成功");
        runRepository.save(run);
        return StepCompletionResult.COMPLETED;
    }

    /** 失败但仍会自动重试的只读步骤，只落步骤错误，不结束 Run。 */
    @Transactional
    public boolean failStepForRetry(String runId, String tenantId, String workerId,
                                    String stepId, String error) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        Step step = findStep(run, stepId);
        step.fail(sanitizer.sanitize(error));
        append(run, step, "STEP_FAILED", step.getError());
        runRepository.save(run);
        return true;
    }

    /** 自动重试前将失败步骤恢复到排队状态，并记录新的尝试。 */
    @Transactional
    public boolean retryStepAutomatically(String runId, String tenantId, String workerId,
                                          String stepId, int nextAttempt) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        Step step = findStep(run, stepId);
        step.retryAutomatically();
        append(run, step, "STEP_RETRY_SCHEDULED", "只读工具瞬态错误，准备第 " + nextAttempt + " 次尝试");
        runRepository.save(run);
        return true;
    }

    /** 普通业务错误和策略拒绝在一个短事务中同时落 Step/Run 终态。 */
    @Transactional
    public boolean failRunWithStep(String runId, String tenantId, String workerId,
                                   String stepId, String error, String stepEventType) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        Step step = findStep(run, stepId);
        boolean needsStepFailure = step.getStatus() == StepStatus.RUNNING
                || step.getStatus() == StepStatus.QUEUED;
        if (needsStepFailure) {
            step.fail(sanitizer.sanitize(error));
            append(run, step, stepEventType == null ? "STEP_FAILED" : stepEventType, step.getError());
        }
        run.fail(sanitizer.sanitize(error));
        append(run, null, "RUN_FAILED", run.getError());
        runRepository.save(run);
        return true;
    }

    /** 超时错误在一个短事务中同时标记步骤和 Run，便于恢复与重试。 */
    @Transactional
    public boolean timeoutRunWithStep(String runId, String tenantId, String workerId,
                                      String stepId, String error) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        Step step = findStep(run, stepId);
        if (step.getStatus() == StepStatus.RUNNING) {
            step.timeout(sanitizer.sanitize(error));
        }
        append(run, step, "STEP_TIMED_OUT", step.getError());
        run.timeout(sanitizer.sanitize(error));
        append(run, null, "RUN_TIMED_OUT", run.getError());
        runRepository.save(run);
        return true;
    }

    /** 任务完成后重新读取所有步骤，确保最终输出来自数据库中的最新成功步骤。 */
    @Transactional
    public boolean finishSuccess(String runId, String tenantId, String workerId) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        String output = run.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                .reduce((left, right) -> right)
                .map(Step::getOutput)
                .orElse("");
        run.succeed(sanitizer.sanitize(output));
        append(run, null, "RUN_SUCCEEDED", "任务执行成功");
        runRepository.save(run);
        return true;
    }

    /** 临时基础设施异常交给 Rabbit 重试，不能把 RUNNING 步骤留给下一次消费。 */
    @Transactional
    public void requeueAfterInfrastructureFailure(String runId, String tenantId,
                                                   String workerId, String stepId) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return;
        }
        if (stepId != null) {
            Step step = findStep(run, stepId);
            if (step.getStatus() == StepStatus.RUNNING) {
                step.requeueAfterInfrastructureFailure();
                append(run, step, "STEP_REQUEUED_AFTER_INFRA_FAILURE", "基础设施故障，等待消息重试");
            }
        }
        run.clearLease();
        runRepository.save(run);
    }

    private Run loadForUpdate(String runId) {
        return runRepository.findByIdForExecutionUpdate(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
    }

    private void assertTenant(Run run, String tenantId) {
        if (tenantId == null || tenantId.isBlank() || !run.getTenantId().equals(tenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权访问其他租户的执行任务");
        }
    }

    private boolean ownsRunningRun(Run run, String workerId) {
        return run.getStatus() == RunStatus.RUNNING
                && workerId != null && workerId.equals(run.getWorkerId())
                && (run.getLeaseUntil() == null || run.getLeaseUntil().isAfter(Instant.now()));
    }

    private Step findStep(Run run, String stepId) {
        return run.getSteps().stream()
                .filter(step -> step.getId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("步骤不存在: " + stepId));
    }

    private boolean exceedsBudget(Run run, BigDecimal additionalCost) {
        if (run.getBudget() == null || additionalCost == null || additionalCost.signum() <= 0) {
            return false;
        }
        BigDecimal currentCost = run.getSteps().stream()
                .map(Step::getCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return currentCost.add(additionalCost).compareTo(run.getBudget()) > 0;
    }

    private void append(Run run, Step step, String eventType, String message) {
        auditTrailService.append(new AuditEvent(
                run.getTenantId(), run.getUserId(), run.getTraceId(), run.getId(),
                step == null ? null : step.getId(), eventType, sanitizer.sanitize(message),
                "status=" + run.getStatus()));
    }

    private RunExecutionSnapshot snapshot(Run run) {
        return new RunExecutionSnapshot(run.getId(), run.getTenantId(), run.getUserId(),
                run.getModelName(), run.getPromptVersion(), run.getInput(), run.getBudget(),
                run.getPermissionsSnapshot(), run.getSteps().stream().map(this::stepSnapshot).toList());
    }

    private StepExecutionSnapshot stepSnapshot(Step step) {
        return new StepExecutionSnapshot(step.getId(), step.getSequence(), step.getType(),
                step.getName(), step.getInput(), step.getStatus(), step.isApprovalGranted());
    }

    public enum StepCompletionResult {
        COMPLETED,
        BUDGET_EXCEEDED,
        CANCELLED_OR_NOT_OWNER
    }

    public record RunExecutionSnapshot(
            String id,
            String tenantId,
            String userId,
            String modelName,
            String promptVersion,
            String input,
            BigDecimal budget,
            String permissionsSnapshot,
            List<StepExecutionSnapshot> steps
    ) {
    }

    public record StepExecutionSnapshot(
            String id,
            int sequence,
            StepType type,
            String name,
            String input,
            StepStatus status,
            boolean approvalGranted
    ) {
    }
}
