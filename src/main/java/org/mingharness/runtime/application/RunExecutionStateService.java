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
import org.mingharness.model.AgentTurnCodec;
import org.mingharness.model.ModelToolCall;
import org.mingharness.runtime.application.RuntimeLimits;
import org.mingharness.tool.ToolAudit;
import org.mingharness.conversation.ConversationMessageWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;

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
    private final AgentTurnCodec agentTurnCodec;
    private final RuntimeLimits runtimeLimits;
    private final TenantPolicyService tenantPolicyService;
    private final ConversationMessageWriter conversationMessageWriter;

    public RunExecutionStateService(RunRepository runRepository,
                                    AuditTrailService auditTrailService,
                                    SensitiveDataSanitizer sanitizer,
                                    AgentTurnCodec agentTurnCodec,
                                    RuntimeLimits runtimeLimits,
                                    TenantPolicyService tenantPolicyService,
                                    ConversationMessageWriter conversationMessageWriter) {
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.sanitizer = sanitizer;
        this.agentTurnCodec = agentTurnCodec;
        this.runtimeLimits = runtimeLimits;
        this.tenantPolicyService = tenantPolicyService;
        this.conversationMessageWriter = conversationMessageWriter;
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

    /** Agent 动态追加步骤后重新读取最新步骤列表，避免 Worker 只处理初始快照。 */
    @Transactional(readOnly = true)
    public Optional<RunExecutionSnapshot> current(String runId, String tenantId, String workerId) {
        Run run = runRepository.findById(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) return Optional.empty();
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
        return completeStep(runId, tenantId, workerId, stepId, output, inputTokens,
                outputTokens, cost, null);
    }

    /** 完成步骤并在同一事务追加工具的结构化执行审计。 */
    @Transactional
    public StepCompletionResult completeStep(String runId, String tenantId, String workerId,
                                             String stepId, String output, int inputTokens,
                                             int outputTokens, BigDecimal cost, ToolAudit toolAudit) {
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
            conversationMessageWriter.updateForTerminalRun(run);
            return StepCompletionResult.BUDGET_EXCEEDED;
        }
        if (step.getStatus() != StepStatus.RUNNING) {
            return StepCompletionResult.CANCELLED_OR_NOT_OWNER;
        }
        step.succeed(sanitizer.sanitize(output), inputTokens, outputTokens, safeCost);
        append(run, step, "STEP_SUCCEEDED", "步骤执行成功");
        if (toolAudit != null) {
            append(run, step, toolAudit.eventType(), toolAudit.message(), toolAudit.metadata());
        }
        runRepository.save(run);
        return StepCompletionResult.COMPLETED;
    }

    /**
     * 将正在生成的模型输出落为短事务快照。模型调用本身仍在事务外，避免持有 Run 行锁。
     */
    @Transactional
    public boolean updateStreamingModelOutput(String runId, String tenantId, String workerId,
                                              String stepId, String persistedOutput,
                                              String conversationContent) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) {
            return false;
        }
        Step step = findStep(run, stepId);
        if (step.getType() != StepType.MODEL || step.getStatus() != StepStatus.RUNNING) {
            return false;
        }
        step.updateRunningOutput(sanitizer.sanitize(persistedOutput));
        conversationMessageWriter.updatePendingContent(run, conversationContent);
        runRepository.save(run);
        return true;
    }

    /** Agent 模型完成后，将模型提出的工具调用持久化为排队步骤。 */
    @Transactional
    public boolean appendAgentToolSteps(String runId, String tenantId, String workerId,
                                        String modelStepId, List<ModelToolCall> calls) {
        if (calls == null || calls.isEmpty()) return true;
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!run.isAgentMode() || !ownsRunningRun(run, workerId)) return false;
        Step modelStep = findStep(run, modelStepId);
        if (modelStep.getStatus() != StepStatus.SUCCEEDED) return false;
        try {
            if (appendAgentToolStepsInternal(run, modelStep, calls)) runRepository.save(run);
            return true;
        } catch (BusinessException exception) {
            if (!"AGENT_DUPLICATE_TOOL_CALL".equals(exception.getCode())) throw exception;
            failDuplicateAgentRun(run, modelStep, exception.getMessage());
            return false;
        }
    }

    /** Worker 在模型结果已提交但进程尚未来得及追加工具步骤时，按已持久化结果补齐后续步骤。 */
    @Transactional
    public boolean recoverAgentToolSteps(String runId, String tenantId, String workerId) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!run.isAgentMode() || !ownsRunningRun(run, workerId)) return false;
        boolean changed = false;
        for (Step modelStep : run.getSteps().stream()
                .filter(step -> step.getType() == StepType.MODEL && step.getStatus() == StepStatus.SUCCEEDED)
                .toList()) {
            AgentTurnCodec.AgentTurn turn = agentTurnCodec.decode(modelStep.getOutput());
            if (!turn.toolCalls().isEmpty()) {
                int before = stepsAfterModel(run, modelStep).size();
                try {
                    appendAgentToolStepsInternal(run, modelStep, turn.toolCalls());
                } catch (BusinessException exception) {
                    if (!"AGENT_DUPLICATE_TOOL_CALL".equals(exception.getCode())) throw exception;
                    failDuplicateAgentRun(run, modelStep, exception.getMessage());
                    return false;
                }
                changed = changed || stepsAfterModel(run, modelStep).size() > before;
            }
        }
        if (changed) runRepository.save(run);
        return changed;
    }

    /** 当前一轮工具全部成功后追加下一轮模型步骤；进程重启后可由数据库重新推导。 */
    @Transactional
    public boolean appendNextAgentModel(String runId, String tenantId, String workerId) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!run.isAgentMode() || !ownsRunningRun(run, workerId)) return false;
        if (run.getSteps().stream().anyMatch(step -> step.getStatus() == StepStatus.QUEUED
                || step.getStatus() == StepStatus.RUNNING
                || step.getStatus() == StepStatus.WAITING_APPROVAL)) {
            return false;
        }
        Step latest = run.getSteps().stream().reduce((left, right) -> right).orElse(null);
        if (latest == null || latest.getType() != StepType.TOOL
                || latest.getStatus() != StepStatus.SUCCEEDED) return false;
        Step latestModel = run.getSteps().stream()
                .filter(step -> step.getType() == StepType.MODEL)
                .reduce((left, right) -> right).orElse(null);
        if (latestModel == null) return false;
        AgentTurnCodec.AgentTurn turn = agentTurnCodec.decode(latestModel.getOutput());
        if (turn.toolCalls().isEmpty()) return false;
        long modelTurns = run.getSteps().stream().filter(step -> step.getType() == StepType.MODEL).count();
        if (modelTurns >= run.getMaxTurns()) {
            run.fail("Agent 达到最大轮数限制: " + run.getMaxTurns());
            append(run, null, "AGENT_MAX_TURNS_EXCEEDED", run.getError());
            runRepository.save(run);
            conversationMessageWriter.updateForTerminalRun(run);
            return false;
        }
        int nextSequence = run.getSteps().stream().mapToInt(Step::getSequence).max().orElse(0) + 1;
        String nextInput = transcript(run);
        Step nextModel = new Step(nextSequence, StepType.MODEL, "model.complete", nextInput);
        run.addStep(nextModel);
        append(run, nextModel, "AGENT_MODEL_TURN_QUEUED", "工具结果已注入下一轮模型上下文");
        runRepository.save(run);
        return true;
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

    /** Worker 执行同批重复 Tool Call 时，只复制首个调用的结果，不再次触发外部副作用。 */
    @Transactional
    public boolean completeReplayedStep(String runId, String tenantId, String workerId,
                                         String stepId, String sourceStepId) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (!ownsRunningRun(run, workerId)) return false;
        Step step = findStep(run, stepId);
        Step source = findStep(run, sourceStepId);
        if (step.getStatus() == StepStatus.SUCCEEDED) return true;
        if (!step.isReplayPending() || !Objects.equals(step.getReplaySourceStepId(), source.getId())) {
            return false;
        }
        if (source.getStatus() != StepStatus.SUCCEEDED) {
            throw new IllegalStateException("复用来源步骤尚未成功: " + source.getId());
        }
        step.replayFrom(source);
        append(run, step, "AGENT_TOOL_CALL_REPLAYED", "复用已成功工具结果",
                "sourceStepId=" + source.getId());
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
        conversationMessageWriter.updateForTerminalRun(run);
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
        conversationMessageWriter.updateForTerminalRun(run);
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
        Optional<String> finalModelError = AgentCompletionPolicy.missingFinalModel(run.getSteps(), agentTurnCodec);
        if (run.isAgentMode() && finalModelError.isPresent()) {
            run.fail(finalModelError.get());
            append(run, null, "AGENT_FINAL_MODEL_REQUIRED", finalModelError.get());
            append(run, null, "RUN_FAILED", run.getError());
            runRepository.save(run);
            conversationMessageWriter.updateForTerminalRun(run);
            return false;
        }
        Optional<String> validationError = AgentVerificationPolicy.missingVerification(run.getSteps().stream()
                .map(step -> new AgentVerificationPolicy.StepEvidence(
                        step.getSequence(), step.getName(), step.getStatus(),
                        AgentToolFailureRecovery.verificationEligible(step.getOutput())))
                .toList());
        if (run.isAgentMode() && validationError.isPresent()) {
            Step latest = run.getSteps().stream()
                    .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                    .reduce((left, right) -> right)
                    .orElse(null);
            if (latest != null && latest.getType() == StepType.MODEL) {
                latest.fail(validationError.get());
            }
            run.fail(validationError.get());
            append(run, latest, "AGENT_VALIDATION_REQUIRED", validationError.get());
            append(run, null, "RUN_FAILED", validationError.get());
            runRepository.save(run);
            conversationMessageWriter.updateForTerminalRun(run);
            return false;
        }
        String output = run.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                .reduce((left, right) -> right)
                .map(Step::getOutput)
                .orElse("");
        if (run.isAgentMode()) {
            output = agentTurnCodec.decode(output).content();
        }
        run.succeed(sanitizer.sanitize(output));
        append(run, null, "RUN_SUCCEEDED", "任务执行成功");
        runRepository.save(run);
        conversationMessageWriter.updateForTerminalRun(run);
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

    /**
     * Outbox 达到最大重试次数后，立即把尚未执行的 Run 标记为失败。
     *
     * <p>消息投递失败不是业务步骤失败，但继续保留 RUNNING 会让用户误以为任务仍在执行，
     * 也会让恢复器只能在租约超时后再处理。该方法使用同一套悲观锁和审计链，确保终态只写入一次。</p>
     */
    @Transactional
    public boolean failAfterDispatchFailure(String runId, String tenantId, String error) {
        Run run = loadForUpdate(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() == RunStatus.SUCCEEDED
                || run.getStatus() == RunStatus.FAILED
                || run.getStatus() == RunStatus.TIMED_OUT
                || run.getStatus() == RunStatus.CANCELLED) {
            return false;
        }
        String safeError = sanitizer.sanitize(error == null || error.isBlank()
                ? "执行消息投递失败，已达到最大重试次数" : error);
        Step pendingStep = run.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.QUEUED
                        || step.getStatus() == StepStatus.RUNNING
                        || step.getStatus() == StepStatus.WAITING_APPROVAL)
                .findFirst()
                .orElse(null);
        if (pendingStep != null) {
            pendingStep.fail(safeError);
            append(run, pendingStep, "DISPATCH_FAILED", safeError);
        }
        run.fail(safeError);
        append(run, null, "RUN_DISPATCH_FAILED", safeError);
        append(run, null, "RUN_FAILED", safeError);
        runRepository.save(run);
        conversationMessageWriter.updateForTerminalRun(run);
        return true;
    }

    private Run loadForUpdate(String runId) {
        return runRepository.findByIdForExecutionUpdate(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
    }

    private void assertTenant(Run run, String tenantId) {
        if (tenantId == null || tenantId.isBlank() || !run.getTenantId().equals(tenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权访问其他组织的执行任务");
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

    private List<Step> stepsAfterModel(Run run, Step modelStep) {
        int nextModelSequence = run.getSteps().stream()
                .filter(step -> step.getType() == StepType.MODEL
                        && step.getSequence() > modelStep.getSequence())
                .mapToInt(Step::getSequence)
                .min().orElse(Integer.MAX_VALUE);
        return run.getSteps().stream()
                .filter(step -> step.getType() == StepType.TOOL
                        && step.getSequence() > modelStep.getSequence()
                        && step.getSequence() < nextModelSequence)
                .sorted(java.util.Comparator.comparingInt(Step::getSequence))
                .toList();
    }

    private boolean appendAgentToolStepsInternal(Run run, Step modelStep, List<ModelToolCall> calls) {
        List<Step> existing = stepsAfterModel(run, modelStep);
        validateExistingAgentSteps(existing, calls);
        int maxSteps = tenantPolicyService.limitsFor(run.getTenantId()).maxStepsPerRun();
        int missing = Math.max(0, calls.size() - existing.size());
        if (run.getSteps().size() + missing > maxSteps) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "AGENT_STEP_LIMIT_EXCEEDED",
                    "Agent 动态步骤超过组织运行上限");
        }
        int nextSequence = run.getSteps().stream().mapToInt(Step::getSequence).max().orElse(0) + 1;
        Map<String, Step> planned = new HashMap<>();
        for (int index = 0; index < existing.size() && index < calls.size(); index++) {
            planned.putIfAbsent(toolCallKey(calls.get(index)), existing.get(index));
        }
        Map<String, Step> reusable = previousSuccessfulTools(run, modelStep);
        // 第一次纯重复批次先复用已成功结果，让模型有机会看到完整的 tool 响应后收敛；
        // 如果上一轮本身已经是纯重放，说明模型仍在空转，此时才终止 Run。
        if (existing.isEmpty() && !reusable.isEmpty()
                && calls.stream().allMatch(call -> reusable.containsKey(toolCallKey(call)))) {
            if (previousModelWasReplayOnly(run, modelStep)) {
                rejectRepeatedToolCalls(run, modelStep, calls);
            }
        }
        for (int index = existing.size(); index < calls.size(); index++) {
            ModelToolCall call = calls.get(index);
            String key = toolCallKey(call);
            Step toolStep = new Step(nextSequence++, StepType.TOOL, call.name(),
                    sanitizer.sanitize(call.arguments()));
            Step plannedSource = planned.get(key);
            Step reusableSource = reusable.get(key);
            String replayEventType = null;
            String replayMessage = null;
            String replayMetadata = null;
            if (plannedSource != null) {
                if (plannedSource.getStatus() == StepStatus.SUCCEEDED) {
                    toolStep.replayFrom(plannedSource);
                    replayEventType = "AGENT_TOOL_CALL_REPLAYED";
                    replayMessage = "复用本轮已成功工具结果";
                    replayMetadata = "sourceStepId=" + plannedSource.getId();
                } else {
                    toolStep.queueReplayFrom(plannedSource.getId());
                    replayEventType = "AGENT_TOOL_CALL_REPLAY_QUEUED";
                    replayMessage = "等待本轮首个相同工具调用完成";
                    replayMetadata = "sourceStepId=" + plannedSource.getId();
                }
            } else if (reusableSource != null) {
                toolStep.replayFrom(reusableSource);
                replayEventType = "AGENT_TOOL_CALL_REPLAYED";
                replayMessage = "复用上一轮已成功工具结果";
                replayMetadata = "sourceStepId=" + reusableSource.getId();
            }
            run.addStep(toolStep);
            append(run, toolStep, "AGENT_TOOL_CALL_REQUESTED", "模型请求调用工具: " + call.name());
            if (replayEventType != null) {
                append(run, toolStep, replayEventType, replayMessage, replayMetadata);
            }
            planned.putIfAbsent(key, toolStep);
        }
        return missing > 0;
    }

    /** 纯重复批次仍然失败，避免模型在复用结果后无界地产生相同轮次。 */
    private void rejectRepeatedToolCalls(Run run, Step modelStep, List<ModelToolCall> calls) {
        String toolName = calls == null || calls.isEmpty() ? "" : calls.get(0).name();
        String message = "模型重复请求已成功执行的工具: " + toolName;
        throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "AGENT_DUPLICATE_TOOL_CALL", message);
    }

    private void failDuplicateAgentRun(Run run, Step modelStep, String reason) {
        String message = sanitizer.sanitize(reason == null || reason.isBlank()
                ? "模型重复请求已成功执行的工具" : reason);
        // 将触发保护的模型步骤标记为失败，确保用户点击“重试”时会重新执行该轮模型，
        // 而不是因为所有已持久化步骤都是 SUCCEEDED 直接落入最终结果校验。
        if (modelStep != null && modelStep.getStatus() == StepStatus.SUCCEEDED) {
            modelStep.fail(message);
            append(run, modelStep, "STEP_FAILED", message);
        }
        append(run, modelStep, "AGENT_DUPLICATE_TOOL_CALL", message);
        run.fail(message);
        append(run, null, "RUN_FAILED", message);
        runRepository.save(run);
        conversationMessageWriter.updateForTerminalRun(run);
    }

    private void validateExistingAgentSteps(List<Step> existing, List<ModelToolCall> calls) {
        int comparable = Math.min(existing.size(), calls.size());
        for (int index = 0; index < comparable; index++) {
            Step step = existing.get(index);
            ModelToolCall call = calls.get(index);
            if (!Objects.equals(toolCallKey(step.getName(), step.getInput()), toolCallKey(call))) {
                throw new BusinessException(HttpStatus.CONFLICT, "AGENT_TOOL_STATE_CONFLICT",
                        "Agent 工具步骤与已持久化模型结果不一致");
            }
        }
    }

    private Map<String, Step> previousSuccessfulTools(Run run, Step modelStep) {
        Step previousModel = run.getSteps().stream()
                .filter(step -> step.getType() == StepType.MODEL
                        && step.getSequence() < modelStep.getSequence()
                        && step.getStatus() == StepStatus.SUCCEEDED)
                .max(java.util.Comparator.comparingInt(Step::getSequence))
                .orElse(null);
        if (previousModel == null) return Map.of();
        Map<String, Step> result = new HashMap<>();
        stepsAfterModel(run, previousModel).stream()
                .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                .forEach(step -> result.putIfAbsent(toolCallKey(step.getName(), step.getInput()), step));
        return result;
    }

    /**
     * 判断紧邻的上一轮模型是否已经只拿重放结果继续请求工具。
     *
     * <p>第一次遇到纯重复调用时仍应给模型一次机会：工具结果本身是有效的，
     * 模型可能只是因为上下文压缩或供应商 Tool Call 偏差重新规划了一遍。若上一轮
     * 的所有工具步骤都已经是重放结果，再次复用只会造成无界空转，因此保留失败保护。</p>
     */
    private boolean previousModelWasReplayOnly(Run run, Step modelStep) {
        Step previousModel = run.getSteps().stream()
                .filter(step -> step.getType() == StepType.MODEL
                        && step.getSequence() < modelStep.getSequence()
                        && step.getStatus() == StepStatus.SUCCEEDED)
                .max(java.util.Comparator.comparingInt(Step::getSequence))
                .orElse(null);
        if (previousModel == null) return false;
        List<Step> previousTools = stepsAfterModel(run, previousModel);
        return !previousTools.isEmpty()
                && previousTools.stream().allMatch(step -> step.getStatus() == StepStatus.SUCCEEDED
                && step.getReplaySourceStepId() != null
                && !step.getReplaySourceStepId().isBlank());
    }

    private String toolCallKey(ModelToolCall call) {
        return toolCallKey(call.name(), sanitizer.sanitize(call.arguments()));
    }

    private String toolCallKey(String name, String arguments) {
        return AgentToolCallKey.of(name, arguments);
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

    private String transcript(Run run) {
        StringBuilder value = new StringBuilder(run.getInput());
        for (Step step : run.getSteps()) {
            if (step.getStatus() != StepStatus.SUCCEEDED && step.getStatus() != StepStatus.REJECTED) continue;
            if (step.getType() == StepType.MODEL) {
                AgentTurnCodec.AgentTurn turn = agentTurnCodec.decode(step.getOutput());
                if (!turn.content().isBlank()) value.append("\n\n模型: ").append(turn.content());
            } else if (step.getType() == StepType.TOOL) {
                value.append("\n\n工具 ").append(step.getName()).append(" 返回: ")
                        .append(step.getOutput() == null ? "" : step.getOutput());
            }
        }
        String text = sanitizer.sanitize(value.toString());
        int max = Math.max(1, runtimeLimits.maxContextChars());
        return AgentTranscriptFormatter.fit(text, max);
    }

    private void append(Run run, Step step, String eventType, String message) {
        append(run, step, eventType, message, "status=" + run.getStatus());
    }

    private void append(Run run, Step step, String eventType, String message, String metadata) {
        auditTrailService.append(new AuditEvent(
                run.getTenantId(), run.getUserId(), run.getTraceId(), run.getId(),
                step == null ? null : step.getId(), eventType, sanitizer.sanitize(message),
                sanitizer.sanitize(metadata)));
    }

    private RunExecutionSnapshot snapshot(Run run) {
        return new RunExecutionSnapshot(run.getId(), run.getTenantId(), run.getUserId(),
                run.getModelName(), run.getModelConfigSnapshotId(), run.getPromptVersion(), run.getInput(), run.getBudget(),
                run.getPermissionsSnapshot(), run.isAgentMode(), run.getMaxTurns(),
                run.getWorkspaceId(),
                run.getSteps().stream()
                        .sorted(Comparator.comparingInt(Step::getSequence))
                        .map(this::stepSnapshot).toList());
    }

    private StepExecutionSnapshot stepSnapshot(Step step) {
        return new StepExecutionSnapshot(step.getId(), step.getSequence(), step.getType(),
                step.getName(), step.getInput(), step.getOutput(), step.getStatus(), step.isApprovalGranted(),
                step.getReplaySourceStepId());
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
            String modelConfigSnapshotId,
            String promptVersion,
            String input,
            BigDecimal budget,
            String permissionsSnapshot,
            boolean agentMode,
            int maxTurns,
            String workspaceId,
            List<StepExecutionSnapshot> steps
    ) {
    }

    public record StepExecutionSnapshot(
            String id,
            int sequence,
            StepType type,
            String name,
            String input,
            String output,
            StepStatus status,
            boolean approvalGranted,
            String replaySourceStepId
    ) {

        /** 兼容未携带 replay 来源的旧 Worker/测试构造方式。 */
        public StepExecutionSnapshot(String id, int sequence, StepType type, String name,
                                     String input, String output, StepStatus status,
                                     boolean approvalGranted) {
            this(id, sequence, type, name, input, output, status, approvalGranted, null);
        }
    }
}
