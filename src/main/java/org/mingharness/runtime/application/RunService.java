package org.mingharness.runtime.application;

import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunPage;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.api.StepView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.domain.TenantPolicyLimits;
import org.mingharness.model.ModelGateway;
import org.mingharness.model.ModelRequest;
import org.mingharness.model.ModelResponse;
import org.mingharness.model.ModelToolDefinition;
import org.mingharness.model.ModelToolCall;
import org.mingharness.model.AgentTurnCodec;
import org.springframework.beans.factory.annotation.Value;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.dashboard.RunDashboardSummary;
import org.mingharness.tool.HarnessTool;
import org.mingharness.tool.ToolDefinition;
import org.mingharness.tool.ToolInputValidator;
import org.mingharness.tool.ToolOutputValidator;
import org.mingharness.tool.ToolRegistry;
import org.mingharness.policy.PolicyContext;
import org.mingharness.policy.PolicyDecision;
import org.mingharness.policy.PolicyDecisionType;
import org.mingharness.policy.PolicyEngine;
import org.mingharness.context.ContextBuilder;
import org.mingharness.context.api.ContextResult;
import org.mingharness.config.RedisProperties;
import org.mingharness.messaging.OutboxService;
import org.mingharness.messaging.RunExecutionMessage;
import org.mingharness.tool.RetryableToolException;
import org.mingharness.tool.ToolExecutionContext;
import org.mingharness.tool.ToolAudit;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataAccessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RunService {

    private final RunRepository runRepository;
    private final AuditTrailService auditTrailService;
    private final ToolRegistry toolRegistry;
    private final ModelGateway modelGateway;
    private final String defaultModel;
    private final String defaultPromptVersion;
    private final String defaultPolicyVersion;
    private final RuntimeLimits runtimeLimits;
    private final TenantPolicyService tenantPolicyService;
    private final PolicyEngine policyEngine;
    private final ToolInputValidator toolInputValidator;
    private final BoundedExecutor boundedExecutor;
    private final ContextBuilder contextBuilder;
    private final TenantRateLimiter tenantRateLimiter;
    private final ToolOutputValidator toolOutputValidator;
    private final OutboxService outboxService;
    private final RunExecutionLock executionLock;
    private final RunExecutionStateService executionStateService;
    private final RunCancellationSignal cancellationSignal;
    private final RunCancellationChecker cancellationChecker;
    private final TenantRunQuotaGuard tenantRunQuotaGuard;
    private final RedisProperties redisProperties;
    private final String executionMode;
    private final String workerId = "worker-" + UUID.randomUUID();
    private final HarnessMetrics metrics;
    private final SensitiveDataSanitizer sanitizer;
    private final AgentTurnCodec agentTurnCodec;
    @PersistenceContext
    private EntityManager entityManager;

    public RunService(RunRepository runRepository,
                      AuditTrailService auditTrailService,
                      ToolRegistry toolRegistry,
                      ModelGateway modelGateway,
                      @Value("${harness.model.name:demo-model}") String defaultModel,
                      @Value("${harness.prompt.version:prompt-v1}") String defaultPromptVersion,
                      @Value("${harness.policy.version:policy-v1}") String defaultPolicyVersion,
                      RuntimeLimits runtimeLimits,
                      TenantPolicyService tenantPolicyService,
                      PolicyEngine policyEngine,
                      ToolInputValidator toolInputValidator,
                      BoundedExecutor boundedExecutor,
                      ContextBuilder contextBuilder,
                      TenantRateLimiter tenantRateLimiter,
                      ToolOutputValidator toolOutputValidator,
                      OutboxService outboxService,
                      RunExecutionLock executionLock,
                      RunExecutionStateService executionStateService,
                      RunCancellationSignal cancellationSignal,
                      RunCancellationChecker cancellationChecker,
                      TenantRunQuotaGuard tenantRunQuotaGuard,
                      RedisProperties redisProperties,
                      @Value("${harness.execution.mode:sync}") String executionMode,
                      HarnessMetrics metrics,
                      SensitiveDataSanitizer sanitizer,
                      AgentTurnCodec agentTurnCodec) {
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.toolRegistry = toolRegistry;
        this.modelGateway = modelGateway;
        this.defaultModel = defaultModel;
        this.defaultPromptVersion = defaultPromptVersion;
        this.defaultPolicyVersion = defaultPolicyVersion;
        this.runtimeLimits = runtimeLimits;
        this.tenantPolicyService = tenantPolicyService;
        this.policyEngine = policyEngine;
        this.toolInputValidator = toolInputValidator;
        this.boundedExecutor = boundedExecutor;
        this.contextBuilder = contextBuilder;
        this.tenantRateLimiter = tenantRateLimiter;
        this.toolOutputValidator = toolOutputValidator;
        this.outboxService = outboxService;
        this.executionLock = executionLock;
        this.executionStateService = executionStateService;
        this.cancellationSignal = cancellationSignal;
        this.cancellationChecker = cancellationChecker;
        this.tenantRunQuotaGuard = tenantRunQuotaGuard;
        this.redisProperties = redisProperties;
        this.executionMode = executionMode;
        this.metrics = metrics;
        this.sanitizer = sanitizer;
        this.agentTurnCodec = agentTurnCodec;
    }

    @Transactional
    public RunSummary create(CreateRunRequest request) {
        String sanitizedTitle = sanitizer.sanitize(request.title());
        String sanitizedInput = sanitizer.sanitize(request.input());
        boolean agentMode = request.isAgentMode();
        String toolName = request.toolName() == null || request.toolName().isBlank()
                ? "demo.echo" : request.toolName();
        if (agentMode) {
            // Agent 输入是自然语言指令，具体工具参数由模型按各工具 schema 生成。
            toolName = "agent.model";
        } else {
            HarnessTool selectedTool = toolRegistry.get(toolName);
            toolInputValidator.validate(selectedTool.definition(), sanitizedInput);
        }
        final boolean effectiveAgentMode = agentMode;
        final String effectiveToolName = toolName;

        if (sanitizer.containsSensitiveData(request.idempotencyKey())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_IDEMPOTENCY_KEY_REJECTED",
                    "幂等键不能包含疑似密钥或凭证");
        }
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        return tenantRunQuotaGuard.withLock(request.tenantId(), () -> {
            TenantPolicyLimits tenantLimits = tenantPolicyService.limitsFor(request.tenantId());
            // 幂等查询必须与配额计数处于同一个租户互斥区，避免并发重复创建或误占用配额。
            if (idempotencyKey != null) {
                Optional<Run> existing = runRepository.findByTenantIdAndIdempotencyKey(
                        request.tenantId(), idempotencyKey);
                if (existing.isPresent()) {
                    if (!sameCreateRequest(existing.get(), request, effectiveToolName, sanitizedTitle, sanitizedInput)) {
                        throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                                "幂等键已经用于其他任务");
                    }
                    return toSummary(existing.get());
                }
            }
            if (!effectiveAgentMode && !tenantLimits.allowsTool(effectiveToolName)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_TOOL_NOT_ALLOWED",
                        "当前租户策略不允许使用工具: " + effectiveToolName);
            }
            // 参数/配额校验失败的请求不应消耗 Redis 或内存速率桶中的合法创建额度。
            validateRuntimeLimits(request, tenantLimits);
            tenantRateLimiter.acquire(request.tenantId(), tenantLimits.maxCreatesPerMinute());

            Run run = new Run(
                    request.tenantId(),
                    request.userId(),
                    sanitizedTitle,
                    sanitizedInput,
                    request.budget() == null ? BigDecimal.ONE : request.budget(),
                    sanitizer.sanitize(valueOrDefault(request.modelName(), defaultModel)),
                    sanitizer.sanitize(valueOrDefault(request.promptVersion(), defaultPromptVersion)),
                    sanitizer.sanitize(valueOrDefault(request.policyVersion(), defaultPolicyVersion)),
                    idempotencyKey,
                    normalizePermissions(request.permissions()),
                    effectiveAgentMode,
                    request.effectiveMaxTurns()
            );
            run.addStep(new Step(1, StepType.MODEL, "model.complete", sanitizedInput));
            if (!effectiveAgentMode) {
                run.addStep(new Step(2, StepType.TOOL, effectiveToolName, sanitizedInput));
            }
            if (run.getSteps().size() > tenantLimits.maxStepsPerRun()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "STEP_LIMIT_EXCEEDED",
                        "任务步骤数超过租户运行上限");
            }
            Run saved = runRepository.save(run);
            metrics.runCreated();
            record(saved.getId(), null, "RUN_CREATED", "创建执行任务");
            return toSummary(saved);
        });
    }

    @Transactional
    public RunDetail start(String runId, String tenantId) {
        Run run = getRun(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() == RunStatus.SUCCEEDED) {
            return toDetail(run);
        }
        if (run.getStatus() != RunStatus.QUEUED) {
            throw new BusinessException(HttpStatus.CONFLICT, "RUN_NOT_STARTABLE", "任务当前状态不能启动: " + run.getStatus());
        }

        run.start();
        runRepository.save(run);
        record(run.getId(), null, "RUN_STARTED", "开始执行任务");

        return dispatch(run, "START");
    }

    @Transactional(readOnly = true)
    public RunDetail getDetail(String runId, String tenantId) {
        Run run = getRun(runId);
        assertTenant(run, tenantId);
        return toDetail(run);
    }

    @Transactional(readOnly = true)
    public List<RunSummary> list(String tenantId) {
        return runRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toSummary).toList();
    }

    /**
     * 按租户分页查询 Run，供历史控制台和外部调用方避免一次加载全部记录。
     *
     * <p>保留旧的 {@link #list(String)} 接口用于兼容已有控制台；分页接口使用
     * createdAt 和 id 的稳定倒序排序，避免同一创建时间的记录在翻页时抖动。</p>
     */
    @Transactional(readOnly = true)
    public RunPage listPage(String tenantId, int page, int size, RunStatus status) {
        if (page < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码不能小于 0");
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "每页数量必须在 1 到 100 之间");
        }
        PageRequest request = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Run> result = status == null
                ? runRepository.findByTenantId(tenantId, request)
                : runRepository.findByTenantIdAndStatus(tenantId, status, request);
        return new RunPage(result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public RunDashboardSummary summary(String tenantId) {
        List<Run> runs = runRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId);
        return new RunDashboardSummary(
                runs.size(),
                countStatus(runs, RunStatus.QUEUED),
                countStatus(runs, RunStatus.RUNNING),
                countStatus(runs, RunStatus.WAITING_APPROVAL),
                countStatus(runs, RunStatus.SUCCEEDED),
                countStatus(runs, RunStatus.FAILED),
                countStatus(runs, RunStatus.CANCELLED),
                runs.stream().flatMap(run -> run.getSteps().stream()).mapToLong(Step::getInputTokens).sum(),
                runs.stream().flatMap(run -> run.getSteps().stream()).mapToLong(Step::getOutputTokens).sum(),
                runs.stream().mapToLong(Run::getDurationMs).sum(),
                runs.stream().flatMap(run -> run.getSteps().stream())
                        .map(Step::getCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @Transactional
    public void cancel(String runId, String tenantId) {
        // 先写入租户绑定的短期信号；即使数据库行正被 Worker 锁定，也能让它在步骤边界停止。
        cancellationSignal.request(runId, tenantId, Duration.ofMillis(runtimeLimits.recoveryTimeoutMs()));
        // 悲观锁查询会在 Worker 释放行锁后读取最新 version，避免取消与步骤完成发生乐观锁竞态。
        Run run = runRepository.findByIdForCancelUpdate(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
        assertTenant(run, tenantId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return;
        }
        if (run.getStatus() == RunStatus.SUCCEEDED
                || run.getStatus() == RunStatus.FAILED
                || run.getStatus() == RunStatus.TIMED_OUT) {
            throw new BusinessException(HttpStatus.CONFLICT, "RUN_NOT_CANCELLABLE", "已结束的任务不能取消");
        }
        run.cancel();
        runRepository.save(run);
        record(run.getId(), null, "RUN_CANCELLED", "取消执行任务");
    }

    @Transactional
    public RunDetail approve(String runId, String tenantId) {
        return approve(runId, tenantId, null);
    }

    @Transactional
    public RunDetail approve(String runId, String tenantId, String approverId) {
        Run run = getRun(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() != RunStatus.WAITING_APPROVAL) {
            throw new BusinessException(HttpStatus.CONFLICT, "RUN_NOT_WAITING_APPROVAL", "任务当前不需要审批");
        }
        Step step = run.getSteps().stream()
                .filter(item -> item.getStatus() == StepStatus.WAITING_APPROVAL)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "APPROVAL_STEP_NOT_FOUND", "找不到待审批步骤"));
        step.approve();
        run.resumeAfterApproval();
        runRepository.save(run);
        String actorId = approverId == null || approverId.isBlank() ? run.getUserId() : approverId;
        record(run.getId(), step.getId(), "APPROVAL_APPROVED", "人工审批通过", actorId,
                approvalSnapshot(step));
        return dispatch(run, "APPROVE");
    }

    @Transactional
    public RunDetail reject(String runId, String tenantId, String reason) {
        return reject(runId, tenantId, reason, null);
    }

    @Transactional
    public RunDetail reject(String runId, String tenantId, String reason, String approverId) {
        Run run = getRun(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() != RunStatus.WAITING_APPROVAL) {
            throw new BusinessException(HttpStatus.CONFLICT, "RUN_NOT_WAITING_APPROVAL", "任务当前不需要审批");
        }
        Step step = run.getSteps().stream()
                .filter(item -> item.getStatus() == StepStatus.WAITING_APPROVAL)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "APPROVAL_STEP_NOT_FOUND", "找不到待审批步骤"));
        String rejectReason = sanitizer.sanitize(reason == null || reason.isBlank() ? "人工审批拒绝" : reason);
        step.fail(rejectReason);
        run.fail(rejectReason);
        runRepository.save(run);
        String actorId = approverId == null || approverId.isBlank() ? run.getUserId() : approverId;
        record(run.getId(), step.getId(), "APPROVAL_REJECTED", rejectReason, actorId,
                approvalSnapshot(step));
        record(run.getId(), null, "RUN_FAILED", rejectReason);
        return toDetail(run);
    }

    @Transactional
    public RunDetail retry(String runId, String tenantId) {
        Run run = getRun(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() != RunStatus.FAILED && run.getStatus() != RunStatus.TIMED_OUT) {
            throw new BusinessException(HttpStatus.CONFLICT, "RUN_NOT_RETRYABLE", "只有失败或超时任务可以重试");
        }
        run.getSteps().forEach(Step::retry);
        run.retry();
        runRepository.save(run);
        record(run.getId(), null, "RUN_RETRY_QUEUED", "任务进入重试队列");
        run.start();
        record(run.getId(), null, "RUN_STARTED", "开始执行重试任务");
        return dispatch(run, "RETRY");
    }

    public void assertTenant(String runId, String tenantId) {
        assertTenant(getRun(runId), tenantId);
    }

    /** Rabbit Worker 调用的入口，重复消息会被执行锁安全丢弃。 */
    public void executeFromWorker(RunExecutionMessage message) {
        if (message == null || message.runId() == null || message.tenantId() == null) {
            throw new IllegalArgumentException("Run 执行消息缺少必要字段");
        }
        Duration lease = workerLockLease();
        Optional<RunExecutionLock.LockToken> lock;
        try {
            lock = executionLock.tryAcquire(message.runId(), lease);
        } catch (RuntimeException exception) {
            metrics.workerInfrastructureFailed();
            throw new TransientInfrastructureException("Run 执行锁基础设施不可用", exception);
        }
        if (lock.isEmpty()) {
            return;
        }
        RunExecutionLock.LockToken token = lock.get();
        try {
            Optional<RunExecutionStateService.RunExecutionSnapshot> claimed = executionStateService.claim(
                    message.runId(), message.tenantId(), workerId, nextRunLeaseUntil());
            if (claimed.isEmpty()) {
                return;
            }
            metrics.workerClaimed();
            metrics.recordWorkerDuration(() -> executePendingOutsideTransaction(claimed.get(), token));
        } catch (RuntimeException exception) {
            metrics.workerFailed();
            throw exception;
        } finally {
            executionLock.release(token);
        }
    }

    /**
     * Rabbit Worker 的执行编排。外部模型和工具调用不包含在数据库事务内，所有状态变更通过
     * {@link RunExecutionStateService} 的短事务完成。
     */
    private void executePendingOutsideTransaction(RunExecutionStateService.RunExecutionSnapshot run,
                                                  RunExecutionLock.LockToken lockToken) {
        String activeStepId = null;
        try {
            while (true) {
                if (run.agentMode()) {
                    executionStateService.recoverAgentToolSteps(run.id(), run.tenantId(), workerId);
                }
                RunExecutionStateService.RunExecutionSnapshot current = run.agentMode()
                        ? executionStateService.current(run.id(), run.tenantId(), workerId).orElse(null)
                        : run;
                if (current == null) return;
                boolean foundPending = false;
                for (RunExecutionStateService.StepExecutionSnapshot step : current.steps()) {
                    if (step.status() == StepStatus.SUCCEEDED) continue;
                    foundPending = true;
                    activeStepId = step.id();
                    ensureNotCancelled(current.id(), current.tenantId());
                    refreshLease(current, lockToken);
                    if (step.type() == StepType.TOOL) {
                        HarnessTool tool = toolRegistry.get(step.name());
                        ToolDefinition definition = tool.definition();
                        PolicyDecision decision = policyEngine.evaluate(new PolicyContext(
                                current.tenantId(), current.userId(), permissions(current.permissionsSnapshot()),
                                step.approvalGranted()), definition);
                        if (decision.type() == PolicyDecisionType.DENY) {
                            if (executionStateService.failRunWithStep(current.id(), current.tenantId(), workerId,
                                    step.id(), decision.reason(), "POLICY_DENIED")) {
                                metrics.runFailed();
                            }
                            return;
                        }
                        if (decision.requiresApproval()) {
                            executionStateService.requestApproval(current.id(), current.tenantId(), workerId,
                                    step.id(), decision.reason());
                            return;
                        }
                    }
                    WorkerStepOutcome outcome = step.type() == StepType.MODEL
                            ? executeModelStepOutsideTransaction(current, step, lockToken)
                            : executeToolStepOutsideTransaction(current, step, lockToken);
                    if (outcome == WorkerStepOutcome.STOP) return;
                    activeStepId = null;
                }
                if (!current.agentMode()) break;
                RunExecutionStateService.RunExecutionSnapshot latest = executionStateService
                        .current(current.id(), current.tenantId(), workerId).orElse(null);
                if (latest == null) return;
                boolean hasPending = latest.steps().stream().anyMatch(step -> step.status() != StepStatus.SUCCEEDED);
                if (hasPending || executionStateService.appendNextAgentModel(
                        latest.id(), latest.tenantId(), workerId)) {
                    continue;
                }
                break;
            }
            ensureNotCancelled(run.id(), run.tenantId());
            if (executionStateService.finishSuccess(run.id(), run.tenantId(), workerId)) {
                metrics.runSucceeded();
            }
        } catch (RunCancellationRequestedException exception) {
            // 取消事务已经是最终事实来源，Worker 不再保存旧对象或写入成功状态。
        } catch (TransientInfrastructureException exception) {
            executionStateService.requeueAfterInfrastructureFailure(run.id(), run.tenantId(), workerId, activeStepId);
            metrics.workerInfrastructureFailed();
            throw exception;
        } catch (DataAccessException exception) {
            // 数据库短事务失败属于可恢复基础设施错误，不能把仍可重试的 Run 误落为 FAILED。
            try {
                executionStateService.requeueAfterInfrastructureFailure(
                        run.id(), run.tenantId(), workerId, activeStepId);
            } catch (RuntimeException requeueException) {
                exception.addSuppressed(requeueException);
            }
            metrics.workerInfrastructureFailed();
            throw new TransientInfrastructureException("Run 状态存储暂时不可用", exception);
        } catch (RuntimeException exception) {
            if (activeStepId == null) {
                throw exception;
            }
            if (executionStateService.failRunWithStep(run.id(), run.tenantId(), workerId,
                    activeStepId, safeError(exception, "步骤执行失败"), "STEP_FAILED")) {
                metrics.runFailed();
            }
        }
    }

    private WorkerStepOutcome executeModelStepOutsideTransaction(
            RunExecutionStateService.RunExecutionSnapshot run,
            RunExecutionStateService.StepExecutionSnapshot step,
            RunExecutionLock.LockToken lockToken) {
        Optional<RunExecutionStateService.StepExecutionSnapshot> started = executionStateService.startStep(
                run.id(), run.tenantId(), workerId, step.id(), nextRunLeaseUntil());
        if (started.isEmpty()) {
            return WorkerStepOutcome.STOP;
        }
        try {
            ContextResult context = contextBuilder.build(run.tenantId(), run.userId(),
                    started.get().input(), runtimeLimits.maxContextChars());
            if (!context.isEmpty()) {
                executionStateService.recordContextRetrieved(run.id(), run.tenantId(), workerId,
                        step.id(), context.evidences().size());
            }
            String modelInput = context.isEmpty()
                    ? started.get().input()
                    : started.get().input() + "\n\n参考资料（请保留来源标记）:\n" + context.text();
            ModelResponse response = boundedExecutor.execute("模型调用", runtimeLimits.modelTimeoutMs(),
                    () -> modelGateway.complete(new ModelRequest(
                            sanitizer.sanitize(modelInput), run.modelName(), run.promptVersion(),
                            run.agentMode() ? availableModelTools(run.tenantId()) : List.of())));
            if (run.agentMode()) {
                validateAgentToolCalls(run, response.toolCalls());
            }
            // 外部调用期间 Redis 锁和数据库租约都可能接近过期，完成步骤前必须再次确认 Worker 所有权。
            refreshLease(run, lockToken);
            String persistedOutput = run.agentMode() ? agentTurnCodec.encode(response) : response.content();
            RunExecutionStateService.StepCompletionResult result = executionStateService.completeStep(
                    run.id(), run.tenantId(), workerId, step.id(), persistedOutput,
                    response.inputTokens(), response.outputTokens(), response.cost(), null);
            if (result == RunExecutionStateService.StepCompletionResult.COMPLETED) {
                if (run.agentMode() && !response.toolCalls().isEmpty()
                        && !executionStateService.appendAgentToolSteps(
                        run.id(), run.tenantId(), workerId, step.id(), response.toolCalls())) {
                    return WorkerStepOutcome.STOP;
                }
                return WorkerStepOutcome.CONTINUE;
            }
            if (result == RunExecutionStateService.StepCompletionResult.BUDGET_EXCEEDED) {
                metrics.runFailed();
            }
            return WorkerStepOutcome.STOP;
        } catch (ExecutionTimeoutException exception) {
            if (executionStateService.timeoutRunWithStep(run.id(), run.tenantId(), workerId,
                    step.id(), safeError(exception, "步骤执行超时"))) {
                metrics.runTimedOut();
            }
            return WorkerStepOutcome.STOP;
        } catch (TransientInfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (executionStateService.failRunWithStep(run.id(), run.tenantId(), workerId,
                    step.id(), safeError(exception, "步骤执行失败"), "STEP_FAILED")) {
                metrics.runFailed();
            }
            return WorkerStepOutcome.STOP;
        }
    }

    private WorkerStepOutcome executeToolStepOutsideTransaction(
            RunExecutionStateService.RunExecutionSnapshot run,
            RunExecutionStateService.StepExecutionSnapshot step,
            RunExecutionLock.LockToken lockToken) {
        HarnessTool tool = toolRegistry.get(step.name());
        ToolDefinition definition = tool.definition();
        ToolExecutionContext executionContext = new ToolExecutionContext(
                run.id(), step.id(), run.tenantId(), run.id() + ":" + step.id() + ":" + step.name());
        // 有副作用的工具禁止自动重试；只读工具也必须显式声明瞬态错误才会重试。
        int maxAttempts = definition.readOnly()
                ? Math.min(definition.maxAttempts(), runtimeLimits.maxToolAttempts()) : 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1 && !executionStateService.retryStepAutomatically(
                    run.id(), run.tenantId(), workerId, step.id(), attempt)) {
                return WorkerStepOutcome.STOP;
            }
            refreshLease(run, lockToken);
            Optional<RunExecutionStateService.StepExecutionSnapshot> started = executionStateService.startStep(
                    run.id(), run.tenantId(), workerId, step.id(), nextRunLeaseUntil());
            if (started.isEmpty()) {
                return WorkerStepOutcome.STOP;
            }
            try {
                String output = boundedExecutor.execute("工具 " + step.name(), definition.timeoutMs(),
                        () -> tool.execute(started.get().input(), executionContext));
                toolOutputValidator.validate(definition, output);
                ToolAudit toolAudit = tool.audit(started.get().input(), output);
                // 工具可能产生外部副作用，只有续租成功后才允许写入本次结果。
                refreshLease(run, lockToken);
                RunExecutionStateService.StepCompletionResult result = executionStateService.completeStep(
                        run.id(), run.tenantId(), workerId, step.id(), output, 0, 0, BigDecimal.ZERO, toolAudit);
                return result == RunExecutionStateService.StepCompletionResult.COMPLETED
                        ? WorkerStepOutcome.CONTINUE : WorkerStepOutcome.STOP;
            } catch (ExecutionTimeoutException exception) {
                if (executionStateService.timeoutRunWithStep(run.id(), run.tenantId(), workerId,
                        step.id(), safeError(exception, "步骤执行超时"))) {
                    metrics.runTimedOut();
                }
                return WorkerStepOutcome.STOP;
            } catch (TransientInfrastructureException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                metrics.toolFailed();
                String error = safeError(exception, "步骤执行失败");
                boolean canRetry = definition.readOnly()
                        && exception instanceof RetryableToolException
                        && attempt < maxAttempts;
                if (!executionStateService.failStepForRetry(run.id(), run.tenantId(), workerId, step.id(), error)) {
                    return WorkerStepOutcome.STOP;
                }
                if (canRetry) {
                    continue;
                }
                if (executionStateService.failRunWithStep(run.id(), run.tenantId(), workerId,
                        step.id(), error, "STEP_FAILED")) {
                    metrics.runFailed();
                }
                return WorkerStepOutcome.STOP;
            }
        }
        return WorkerStepOutcome.STOP;
    }

    private void refreshLease(RunExecutionStateService.RunExecutionSnapshot run,
                              RunExecutionLock.LockToken lockToken) {
        Duration lease = workerLockLease();
        try {
            if (!executionLock.renew(lockToken, lease)) {
                throw new TransientInfrastructureException("Run 执行锁已丢失，任务将等待恢复处理");
            }
        } catch (TransientInfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TransientInfrastructureException("Run 执行锁续租基础设施不可用", exception);
        }
        if (!executionStateService.heartbeat(run.id(), run.tenantId(), workerId, nextRunLeaseUntil())) {
            throw new RunCancellationRequestedException(run.id());
        }
    }

    /** Worker 互斥锁至少覆盖一次恢复窗口，避免长工具调用期间锁提前过期而重复副作用。 */
    private Duration workerLockLease() {
        return Duration.ofMillis(Math.max(redisProperties.lockTtlMs(), runtimeLimits.recoveryTimeoutMs()));
    }

    /** 数据库恢复租约覆盖完整外部步骤，不能直接复用较短的 Redis 互斥锁租期。 */
    private Instant nextRunLeaseUntil() {
        return Instant.now().plusMillis(Math.max(redisProperties.lockTtlMs(), runtimeLimits.recoveryTimeoutMs()));
    }

    private enum WorkerStepOutcome {
        CONTINUE,
        STOP
    }

    private void executeStep(Run run, Step step, RunExecutionLock.LockToken lockToken) {
        ensureNotCancelled(run);
        if (step.getStatus() == StepStatus.SUCCEEDED) {
            return;
        }
        if (step.getType() == StepType.MODEL) {
            executeModelStep(run, step, lockToken);
            return;
        }
        executeToolStep(run, step, lockToken);
    }

    private void executeModelStep(Run run, Step step, RunExecutionLock.LockToken lockToken) {
        refreshLease(run, lockToken);
        step.start();
        runRepository.save(run);
        record(run.getId(), step.getId(), "STEP_STARTED", "开始执行步骤: " + step.getName());
        try {
            if (step.getType() == StepType.MODEL) {
                ContextResult context = contextBuilder.build(run.getTenantId(), run.getUserId(),
                        step.getInput(), runtimeLimits.maxContextChars());
                if (!context.isEmpty()) {
                    record(run.getId(), step.getId(), "CONTEXT_RETRIEVED",
                            "检索到 " + context.evidences().size() + " 条授权来源");
                }
                String modelInput = context.isEmpty()
                        ? step.getInput()
                        : step.getInput() + "\n\n参考资料（请保留来源标记）:\n" + context.text();
                String safeModelInput = sanitizer.sanitize(modelInput);
                ModelResponse response = boundedExecutor.execute("模型调用", runtimeLimits.modelTimeoutMs(),
                        () -> modelGateway.complete(new ModelRequest(
                                safeModelInput, run.getModelName(), run.getPromptVersion(),
                                run.isAgentMode() ? availableModelTools(run.getTenantId()) : List.of())));
                if (run.isAgentMode()) {
                    validateAgentToolCalls(new RunExecutionStateService.RunExecutionSnapshot(
                            run.getId(), run.getTenantId(), run.getUserId(), run.getModelName(),
                            run.getPromptVersion(), run.getInput(), run.getBudget(),
                            run.getPermissionsSnapshot(), true, run.getMaxTurns(), List.of()), response.toolCalls());
                }
                if (exceedsBudget(run, response.cost())) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "RUN_BUDGET_EXCEEDED",
                            "模型调用成本超过 Run 预算");
                }
                step.succeed(sanitizer.sanitize(run.isAgentMode()
                                ? agentTurnCodec.encode(response) : response.content()),
                        response.inputTokens(), response.outputTokens(), response.cost());
                if (run.isAgentMode() && !response.toolCalls().isEmpty()) {
                    appendAgentToolStepsInMemory(run, step, response.toolCalls());
                }
            }
            record(run.getId(), step.getId(), "STEP_SUCCEEDED", "步骤执行成功");
        } catch (ExecutionTimeoutException exception) {
            step.timeout(safeError(exception, "步骤执行超时"));
            record(run.getId(), step.getId(), "STEP_TIMED_OUT", step.getError());
            throw exception;
        } catch (Exception exception) {
            step.fail(safeError(exception, "步骤执行失败"));
            String eventType = exception instanceof BusinessException businessException
                    && "RUN_BUDGET_EXCEEDED".equals(businessException.getCode())
                    ? "BUDGET_EXCEEDED" : "STEP_FAILED";
            record(run.getId(), step.getId(), eventType, step.getError());
            throw exception;
        }
    }

    private void appendAgentToolStepsInMemory(Run run, Step modelStep, List<ModelToolCall> calls) {
        TenantPolicyLimits limits = tenantPolicyService.limitsFor(run.getTenantId());
        if (run.getSteps().size() + calls.size() > limits.maxStepsPerRun()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "STEP_LIMIT_EXCEEDED",
                    "Agent 动态步骤超过租户运行上限");
        }
        int sequence = run.getSteps().stream().mapToInt(Step::getSequence).max().orElse(0) + 1;
        for (ModelToolCall call : calls) {
            Step toolStep = new Step(sequence++, StepType.TOOL, call.name(), sanitizer.sanitize(call.arguments()));
            run.addStep(toolStep);
            record(run.getId(), toolStep.getId(), "AGENT_TOOL_CALL_REQUESTED",
                    "模型请求调用工具: " + call.name());
        }
    }

    private boolean appendNextAgentModelInMemory(Run run) {
        Step latest = run.getSteps().isEmpty() ? null : run.getSteps().get(run.getSteps().size() - 1);
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
            String message = "Agent 达到最大轮数限制: " + run.getMaxTurns();
            record(run.getId(), null, "AGENT_MAX_TURNS_EXCEEDED", message);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "AGENT_MAX_TURNS_EXCEEDED",
                    message);
        }
        int sequence = run.getSteps().stream().mapToInt(Step::getSequence).max().orElse(0) + 1;
        Step next = new Step(sequence, StepType.MODEL, "model.complete", agentTranscript(run));
        run.addStep(next);
        record(run.getId(), next.getId(), "AGENT_MODEL_TURN_QUEUED", "工具结果已注入下一轮模型上下文");
        return true;
    }

    private String agentTranscript(Run run) {
        StringBuilder value = new StringBuilder(run.getInput());
        for (Step step : run.getSteps()) {
            if (step.getStatus() != StepStatus.SUCCEEDED) continue;
            if (step.getType() == StepType.MODEL) {
                String content = agentTurnCodec.decode(step.getOutput()).content();
                if (!content.isBlank()) value.append("\n\n模型: ").append(content);
            } else if (step.getType() == StepType.TOOL) {
                value.append("\n\n工具 ").append(step.getName()).append(" 返回: ")
                        .append(step.getOutput() == null ? "" : step.getOutput());
            }
        }
        String text = sanitizer.sanitize(value.toString());
        int max = Math.max(1, runtimeLimits.maxContextChars());
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private void executeToolStep(Run run, Step step, RunExecutionLock.LockToken lockToken) {
        HarnessTool tool = toolRegistry.get(step.getName());
        ToolDefinition definition = tool.definition();
        ToolExecutionContext executionContext = new ToolExecutionContext(
                run.getId(), step.getId(), run.getTenantId(),
                run.getId() + ":" + step.getId() + ":" + step.getName());
        // 有副作用的工具禁止自动重试；只读工具也必须显式声明瞬态错误才会重试。
        int maxAttempts = definition.readOnly()
                ? Math.min(definition.maxAttempts(), runtimeLimits.maxToolAttempts()) : 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                step.retryAutomatically();
                record(run.getId(), step.getId(), "STEP_RETRY_SCHEDULED",
                        "只读工具瞬态错误，准备第 " + attempt + " 次尝试");
            }
            refreshLease(run, lockToken);
            step.start();
            runRepository.save(run);
            record(run.getId(), step.getId(), "STEP_STARTED", "开始执行步骤: " + step.getName()
                    + "（第 " + attempt + " 次尝试）");
            try {
                String output = boundedExecutor.execute("工具 " + step.getName(), definition.timeoutMs(),
                        () -> tool.execute(step.getInput(), executionContext));
                toolOutputValidator.validate(definition, output);
                ToolAudit toolAudit = tool.audit(step.getInput(), output);
                step.succeed(sanitizer.sanitize(output));
                record(run.getId(), step.getId(), "STEP_SUCCEEDED", "步骤执行成功");
                if (toolAudit != null) {
                    record(run.getId(), step.getId(), toolAudit.eventType(), toolAudit.message(),
                            run.getUserId(), toolAudit.metadata());
                }
                return;
            } catch (ExecutionTimeoutException exception) {
                step.timeout(safeError(exception, "步骤执行超时"));
                record(run.getId(), step.getId(), "STEP_TIMED_OUT", step.getError());
                throw exception;
            } catch (Exception exception) {
                metrics.toolFailed();
                step.fail(safeError(exception, "步骤执行失败"));
                record(run.getId(), step.getId(), "STEP_FAILED", step.getError());
                boolean canRetry = definition.readOnly()
                        && exception instanceof RetryableToolException
                        && attempt < maxAttempts;
                if (!canRetry) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("工具执行未产生结果: " + step.getName());
    }

    private RunDetail executePending(Run run) {
        return executePending(run, null);
    }

    private RunDetail executePending(Run run, RunExecutionLock.LockToken lockToken) {
        if (run.isAgentMode()) {
            return executeAgentPending(run, lockToken);
        }
        try {
            for (Step step : List.copyOf(run.getSteps())) {
                if (run.getStatus() == RunStatus.CANCELLED) {
                    run.clearLease();
                    return toDetail(runRepository.save(run));
                }
                ensureNotCancelled(run);
                if (step.getStatus() == StepStatus.SUCCEEDED) {
                    continue;
                }
                refreshLease(run, lockToken);
                if (step.getType() == StepType.TOOL) {
                    HarnessTool tool = toolRegistry.get(step.getName());
                    ToolDefinition definition = tool.definition();
                    PolicyDecision decision = policyEngine.evaluate(
                            new PolicyContext(run.getTenantId(), run.getUserId(), permissions(run), step.isApprovalGranted()),
                            definition);
                    if (decision.type() == PolicyDecisionType.DENY) {
                        step.fail(decision.reason());
                        record(run.getId(), step.getId(), "POLICY_DENIED", decision.reason());
                        throw new BusinessException(HttpStatus.FORBIDDEN, "POLICY_DENIED", decision.reason());
                    }
                    if (decision.requiresApproval()) {
                        step.requestApproval();
                        run.waitApproval();
                        run.clearLease();
                        runRepository.save(run);
                        record(run.getId(), step.getId(), "APPROVAL_REQUESTED", decision.reason());
                        return toDetail(run);
                    }
                }
                executeStep(run, step, lockToken);
            }
            String output = run.getSteps().stream()
                    .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                    .reduce((left, right) -> right)
                    .map(Step::getOutput)
                    .orElse("");
            ensureNotCancelled(run);
            run.succeed(output);
            record(run.getId(), null, "RUN_SUCCEEDED", "任务执行成功");
        } catch (RunCancellationRequestedException exception) {
            // 丢弃当前事务中缓存的旧 Run，避免在取消事务提交后又把 RUNNING 写回数据库。
            entityManager.clear();
            return toDetail(runRepository.findById(run.getId()).orElseThrow());
        } catch (ExecutionTimeoutException exception) {
            run.timeout(safeError(exception, "步骤执行超时"));
            metrics.runTimedOut();
            runRepository.save(run);
            record(run.getId(), null, "RUN_TIMED_OUT", run.getError());
        } catch (TransientInfrastructureException exception) {
            metrics.workerInfrastructureFailed();
            throw exception;
        } catch (RuntimeException exception) {
            run.fail(safeError(exception, exception.toString()));
            metrics.runFailed();
            runRepository.save(run);
            record(run.getId(), null, "RUN_FAILED", run.getError());
        }
        if (lockToken != null) {
            run.clearLease();
        }
        if (run.getStatus() == RunStatus.SUCCEEDED) {
            metrics.runSucceeded();
        }
        return toDetail(runRepository.save(run));
    }

    /** 本地同步模式的 Agent 编排，和 Rabbit Worker 使用同样的动态步骤语义。 */
    private RunDetail executeAgentPending(Run run, RunExecutionLock.LockToken lockToken) {
        try {
            int index = 0;
            while (true) {
                while (index < run.getSteps().size()) {
                    Step step = run.getSteps().get(index++);
                    if (step.getStatus() == StepStatus.SUCCEEDED) continue;
                    if (run.getStatus() == RunStatus.CANCELLED) {
                        run.clearLease();
                        return toDetail(runRepository.save(run));
                    }
                    ensureNotCancelled(run);
                    refreshLease(run, lockToken);
                    if (step.getType() == StepType.TOOL) {
                        PolicyDecision decision = policyEngine.evaluate(
                                new PolicyContext(run.getTenantId(), run.getUserId(), permissions(run),
                                        step.isApprovalGranted()), toolRegistry.get(step.getName()).definition());
                        if (decision.type() == PolicyDecisionType.DENY) {
                            step.fail(decision.reason());
                            record(run.getId(), step.getId(), "POLICY_DENIED", decision.reason());
                            throw new BusinessException(HttpStatus.FORBIDDEN, "POLICY_DENIED", decision.reason());
                        }
                        if (decision.requiresApproval()) {
                            step.requestApproval();
                            run.waitApproval();
                            run.clearLease();
                            runRepository.save(run);
                            record(run.getId(), step.getId(), "APPROVAL_REQUESTED", decision.reason());
                            return toDetail(run);
                        }
                    }
                    executeStep(run, step, lockToken);
                }
                if (!appendNextAgentModelInMemory(run)) break;
                index = 0;
            }
            ensureNotCancelled(run);
            String output = run.getSteps().stream()
                    .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                    .reduce((left, right) -> right)
                    .map(Step::getOutput)
                    .map(agentTurnCodec::decode)
                    .map(AgentTurnCodec.AgentTurn::content)
                    .orElse("");
            run.succeed(output);
            record(run.getId(), null, "RUN_SUCCEEDED", "Agent 任务执行成功");
        } catch (RunCancellationRequestedException exception) {
            return toDetail(runRepository.findById(run.getId()).orElseThrow());
        } catch (ExecutionTimeoutException exception) {
            run.timeout(safeError(exception, "步骤执行超时"));
            metrics.runTimedOut();
            runRepository.save(run);
            record(run.getId(), null, "RUN_TIMED_OUT", run.getError());
        } catch (TransientInfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            run.fail(safeError(exception, exception.toString()));
            metrics.runFailed();
            runRepository.save(run);
            record(run.getId(), null, "RUN_FAILED", run.getError());
        }
        if (lockToken != null) run.clearLease();
        if (run.getStatus() == RunStatus.SUCCEEDED) metrics.runSucceeded();
        return toDetail(runRepository.save(run));
    }

    private RunDetail dispatch(Run run, String command) {
        if ("rabbit".equalsIgnoreCase(executionMode)) {
            outboxService.enqueue(run, command);
            return toDetail(runRepository.save(run));
        }
        return executePending(run);
    }

    private void refreshLease(Run run, RunExecutionLock.LockToken lockToken) {
        if (lockToken == null) {
            return;
        }
        Duration lease = Duration.ofMillis(redisProperties.lockTtlMs());
        try {
            if (!executionLock.renew(lockToken, lease)) {
                throw new TransientInfrastructureException("Run 执行锁已丢失，任务将等待恢复处理");
            }
        } catch (TransientInfrastructureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TransientInfrastructureException("Run 执行锁续租基础设施不可用", exception);
        }
        run.heartbeat(workerId, Instant.now().plus(lease));
        runRepository.save(run);
    }

    /** 在每个步骤边界同时检查跨实例信号和数据库最终状态。 */
    private void ensureNotCancelled(Run run) {
        ensureNotCancelled(run.getId(), run.getTenantId());
    }

    /** Worker 短事务编排使用 ID 读取最新取消事实，避免依赖旧 JPA 实体。 */
    private void ensureNotCancelled(String runId, String tenantId) {
        if (cancellationSignal.isRequested(runId, tenantId)
                || cancellationChecker.isCancelled(runId)) {
            throw new RunCancellationRequestedException(runId);
        }
    }

    private Run getRun(String runId) {
        return runRepository.findById(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
    }

    private void assertTenant(Run run, String tenantId) {
        if (tenantId == null || tenantId.isBlank() || !run.getTenantId().equals(tenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权访问其他租户的执行任务");
        }
    }

    private void record(String runId, String stepId, String eventType, String message) {
        Run run = runRepository.findById(runId).orElse(null);
        record(runId, stepId, eventType, message,
                run == null ? null : run.getUserId(), null);
    }

    private void record(String runId, String stepId, String eventType, String message,
                        String actorId, String metadata) {
        Run run = runRepository.findById(runId).orElse(null);
        auditTrailService.append(new AuditEvent(
                run == null ? null : run.getTenantId(),
                actorId,
                run == null ? null : run.getTraceId(),
                runId, stepId, eventType, message,
                metadata == null
                        ? run == null ? null : "{\"status\":\"" + run.getStatus() + "\"}"
                        : metadata
        ));
    }

    private String approvalSnapshot(Step step) {
        return sanitizer.sanitize("tool=" + step.getName() + ";input=" + step.getInput());
    }

    private RunDetail toDetail(Run run) {
        return new RunDetail(
                toSummary(run),
                run.getSteps().stream().map(step -> new StepView(
                        step.getId(), step.getSequence(), step.getType(), step.getStatus(), step.getName(),
                        step.getInput(), step.getOutput(), step.getError(), step.getAttempt(),
                        step.getInputTokens(), step.getOutputTokens(),
                        step.getStartedAt(), step.getFinishedAt(), step.getSpanId(),
                        step.getDurationMs(), step.getCost()
                )).toList()
        );
    }

    private RunSummary toSummary(Run run) {
        return new RunSummary(
                run.getId(), run.getTenantId(), run.getUserId(), run.getTitle(), run.getModelName(),
                run.getPromptVersion(), run.getPolicyVersion(), run.getInput(),
                run.getOutput(), run.getError(), run.getStatus(), run.getBudget(), run.getCreatedAt(),
                run.getUpdatedAt(), run.getSteps().size(), run.getIdempotencyKey(), run.getTraceId(),
                run.getDurationMs(), run.getSteps().stream().map(Step::getCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add), run.isAgentMode(), run.getMaxTurns()
        );
    }

    private void validateRuntimeLimits(CreateRunRequest request, TenantPolicyLimits tenantLimits) {
        // 资源边界按原始请求计算，不能因为脱敏后文本变短而绕过输入大小限制。
        if (request.input() != null && request.input().length() > tenantLimits.maxInputLength()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "INPUT_TOO_LARGE",
                    "任务输入超过允许的最大长度");
        }
        BigDecimal budget = request.budget() == null ? BigDecimal.ONE : request.budget();
        if (budget.compareTo(tenantLimits.maxBudget()) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "BUDGET_EXCEEDED",
                    "任务预算超过运行上限");
        }
        long activeRuns = runRepository.countByTenantIdAndStatusIn(
                request.tenantId(), List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.WAITING_APPROVAL));
        if (activeRuns >= tenantLimits.maxActiveRuns()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TENANT_RUN_QUOTA_EXCEEDED",
                    "租户当前运行数已达到上限");
        }
    }

    /** 模型调用完成后再核对成本，确保实际计费不会静默超过 Run 预算。 */
    private boolean exceedsBudget(Run run, BigDecimal additionalCost) {
        if (run.getBudget() == null || additionalCost == null || additionalCost.signum() <= 0) {
            return false;
        }
        BigDecimal currentCost = run.getSteps().stream()
                .map(Step::getCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return currentCost.add(additionalCost).compareTo(run.getBudget()) > 0;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private String normalizePermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return "";
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Set<String> permissions(Run run) {
        return permissions(run.getPermissionsSnapshot());
    }

    private Set<String> permissions(String permissionsSnapshot) {
        if (permissionsSnapshot == null || permissionsSnapshot.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissionsSnapshot.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 只把当前租户白名单内的工具契约发给模型，权限/审批字段永远不会泄露给供应商。 */
    private List<ModelToolDefinition> availableModelTools(String tenantId) {
        TenantPolicyLimits limits = tenantPolicyService.limitsFor(tenantId);
        return toolRegistry.definitions().stream()
                .filter(definition -> limits.allowsTool(definition.name()))
                .filter(definition -> toolRegistry.get(definition.name()).available())
                .map(definition -> new ModelToolDefinition(
                        definition.name(), definition.description(), definition.inputSchema()))
                .toList();
    }

    /** 模型提出的工具调用先做注册表、租户白名单和 JSON Schema 校验，再进入持久化流程。 */
    private void validateAgentToolCalls(RunExecutionStateService.RunExecutionSnapshot run,
                                        List<ModelToolCall> calls) {
        if (calls == null) return;
        TenantPolicyLimits limits = tenantPolicyService.limitsFor(run.tenantId());
        for (ModelToolCall call : calls) {
            HarnessTool tool = toolRegistry.get(call.name());
            if (!limits.allowsTool(call.name())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_TOOL_NOT_ALLOWED",
                        "当前租户策略不允许使用工具: " + call.name());
            }
            toolInputValidator.validate(tool.definition(), call.arguments());
        }
    }

    private boolean sameCreateRequest(Run run, CreateRunRequest request, String toolName,
                                      String sanitizedTitle, String sanitizedInput) {
        String modelName = sanitizer.sanitize(valueOrDefault(request.modelName(), defaultModel));
        String promptVersion = sanitizer.sanitize(valueOrDefault(request.promptVersion(), defaultPromptVersion));
        String policyVersion = sanitizer.sanitize(valueOrDefault(request.policyVersion(), defaultPolicyVersion));
        return Objects.equals(run.getUserId(), request.userId())
                && Objects.equals(run.getTitle(), sanitizedTitle)
                && Objects.equals(run.getInput(), sanitizedInput)
                && Objects.equals(run.getModelName(), modelName)
                && Objects.equals(run.getPromptVersion(), promptVersion)
                && Objects.equals(run.getPolicyVersion(), policyVersion)
                && run.isAgentMode() == request.isAgentMode()
                && (!request.isAgentMode() || run.getMaxTurns() == request.effectiveMaxTurns())
                // 权限快照属于执行语义的一部分，幂等键不能被低权限/高权限请求混用。
                && Objects.equals(run.getPermissionsSnapshot(), normalizePermissions(request.permissions()))
                && run.getBudget().compareTo(request.budget() == null ? BigDecimal.ONE : request.budget()) == 0
                && (request.isAgentMode()
                || run.getSteps().stream().anyMatch(step -> Objects.equals(step.getName(), toolName)));
    }

    /** 外部模型、工具和网络库的异常可能携带请求头或连接串，持久化前必须脱敏。 */
    private String safeError(Exception exception, String fallback) {
        String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback : exception.getMessage();
        return sanitizer.sanitize(message);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long countStatus(List<Run> runs, RunStatus status) {
        return runs.stream().filter(run -> run.getStatus() == status).count();
    }
}
