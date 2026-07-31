package org.mingharness.runtime.application;

import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.api.RunSummary;
import org.mingharness.runtime.api.StepView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.model.ModelGateway;
import org.mingharness.model.ModelRequest;
import org.mingharness.model.ModelResponse;
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
import org.mingharness.tool.ToolExecutionContext;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuditEventRepository auditEventRepository;
    private final ToolRegistry toolRegistry;
    private final ModelGateway modelGateway;
    private final String defaultModel;
    private final String defaultPromptVersion;
    private final String defaultPolicyVersion;
    private final RuntimeLimits runtimeLimits;
    private final PolicyEngine policyEngine;
    private final ToolInputValidator toolInputValidator;
    private final BoundedExecutor boundedExecutor;
    private final ContextBuilder contextBuilder;
    private final TenantRateLimiter tenantRateLimiter;
    private final ToolOutputValidator toolOutputValidator;
    private final OutboxService outboxService;
    private final RunExecutionLock executionLock;
    private final RedisProperties redisProperties;
    private final String executionMode;
    private final String workerId = "worker-" + UUID.randomUUID();
    private final HarnessMetrics metrics;

    public RunService(RunRepository runRepository,
                      AuditEventRepository auditEventRepository,
                      ToolRegistry toolRegistry,
                      ModelGateway modelGateway,
                      @Value("${harness.model.name:demo-model}") String defaultModel,
                      @Value("${harness.prompt.version:prompt-v1}") String defaultPromptVersion,
                      @Value("${harness.policy.version:policy-v1}") String defaultPolicyVersion,
                      RuntimeLimits runtimeLimits,
                      PolicyEngine policyEngine,
                      ToolInputValidator toolInputValidator,
                      BoundedExecutor boundedExecutor,
                      ContextBuilder contextBuilder,
                      TenantRateLimiter tenantRateLimiter,
                      ToolOutputValidator toolOutputValidator,
                      OutboxService outboxService,
                      RunExecutionLock executionLock,
                      RedisProperties redisProperties,
                      @Value("${harness.execution.mode:sync}") String executionMode,
                      HarnessMetrics metrics) {
        this.runRepository = runRepository;
        this.auditEventRepository = auditEventRepository;
        this.toolRegistry = toolRegistry;
        this.modelGateway = modelGateway;
        this.defaultModel = defaultModel;
        this.defaultPromptVersion = defaultPromptVersion;
        this.defaultPolicyVersion = defaultPolicyVersion;
        this.runtimeLimits = runtimeLimits;
        this.policyEngine = policyEngine;
        this.toolInputValidator = toolInputValidator;
        this.boundedExecutor = boundedExecutor;
        this.contextBuilder = contextBuilder;
        this.tenantRateLimiter = tenantRateLimiter;
        this.toolOutputValidator = toolOutputValidator;
        this.outboxService = outboxService;
        this.executionLock = executionLock;
        this.redisProperties = redisProperties;
        this.executionMode = executionMode;
        this.metrics = metrics;
    }

    @Transactional
    public RunSummary create(CreateRunRequest request) {
        String toolName = request.toolName() == null || request.toolName().isBlank()
                ? "demo.echo" : request.toolName();
        HarnessTool selectedTool = toolRegistry.get(toolName);
        toolInputValidator.validate(selectedTool.definition(), request.input());

        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<Run> existing = runRepository.findByTenantIdAndIdempotencyKey(request.tenantId(), idempotencyKey);
            if (existing.isPresent()) {
                if (!sameCreateRequest(existing.get(), request, toolName)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                            "幂等键已经用于其他任务");
                }
                return toSummary(existing.get());
            }
        }
        tenantRateLimiter.acquire(request.tenantId());
        validateRuntimeLimits(request);

        Run run = new Run(
                request.tenantId(),
                request.userId(),
                request.title(),
                request.input(),
                request.budget() == null ? BigDecimal.ONE : request.budget(),
                valueOrDefault(request.modelName(), defaultModel),
                valueOrDefault(request.promptVersion(), defaultPromptVersion),
                valueOrDefault(request.policyVersion(), defaultPolicyVersion),
                idempotencyKey,
                normalizePermissions(request.permissions())
        );
        run.addStep(new Step(1, StepType.MODEL, "model.complete", request.input()));
        run.addStep(new Step(2, StepType.TOOL, toolName, request.input()));
        if (run.getSteps().size() > runtimeLimits.maxStepsPerRun()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "STEP_LIMIT_EXCEEDED",
                    "任务步骤数超过租户运行上限");
        }
        Run saved = runRepository.save(run);
        metrics.runCreated();
        record(saved.getId(), null, "RUN_CREATED", "创建执行任务");
        return toSummary(saved);
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
        Run run = getRun(runId);
        assertTenant(run, tenantId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return;
        }
        if (run.getStatus() == RunStatus.SUCCEEDED || run.getStatus() == RunStatus.FAILED) {
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
        String rejectReason = reason == null || reason.isBlank() ? "人工审批拒绝" : reason;
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
    @Transactional
    public void executeFromWorker(RunExecutionMessage message) {
        if (message == null || message.runId() == null || message.tenantId() == null) {
            throw new IllegalArgumentException("Run 执行消息缺少必要字段");
        }
        Run run = getRun(message.runId());
        assertTenant(run, message.tenantId());
        if (run.getStatus() != RunStatus.RUNNING) {
            return;
        }
        Duration lease = Duration.ofMillis(redisProperties.lockTtlMs());
        Optional<RunExecutionLock.LockToken> lock = executionLock.tryAcquire(run.getId(), lease);
        if (lock.isEmpty()) {
            return;
        }
        RunExecutionLock.LockToken token = lock.get();
        try {
            run.claim(workerId, Instant.now().plus(lease));
            runRepository.save(run);
            record(run.getId(), null, "WORKER_CLAIMED", "Worker 已获取执行租约");
            metrics.workerClaimed();
            metrics.recordWorkerDuration(() -> executePending(run, token));
        } catch (RuntimeException exception) {
            metrics.workerFailed();
            throw exception;
        } finally {
            executionLock.release(token);
        }
    }

    private void executeStep(Run run, Step step, RunExecutionLock.LockToken lockToken) {
        if (step.getStatus() == StepStatus.SUCCEEDED) {
            return;
        }
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
                ModelResponse response = boundedExecutor.execute("模型调用", runtimeLimits.modelTimeoutMs(),
                        () -> modelGateway.complete(new ModelRequest(
                                modelInput, run.getModelName(), run.getPromptVersion())));
                step.succeed(response.content(), response.inputTokens(), response.outputTokens(), response.cost());
            } else {
                HarnessTool tool = toolRegistry.get(step.getName());
                ToolExecutionContext executionContext = new ToolExecutionContext(
                        run.getId(), step.getId(), run.getTenantId(),
                        run.getId() + ":" + step.getId() + ":" + step.getName());
                String output = boundedExecutor.execute("工具 " + step.getName(), tool.definition().timeoutMs(),
                        () -> tool.execute(step.getInput(), executionContext));
                toolOutputValidator.validate(tool.definition(), output);
                step.succeed(output);
            }
            record(run.getId(), step.getId(), "STEP_SUCCEEDED", "步骤执行成功");
        } catch (ExecutionTimeoutException exception) {
            step.timeout(exception.getMessage());
            record(run.getId(), step.getId(), "STEP_TIMED_OUT", step.getError());
            throw exception;
        } catch (Exception exception) {
            if (step.getType() == StepType.TOOL) {
                metrics.toolFailed();
            }
            step.fail(exception.getMessage() == null ? "步骤执行失败" : exception.getMessage());
            record(run.getId(), step.getId(), "STEP_FAILED", step.getError());
            throw exception;
        }
    }

    private RunDetail executePending(Run run) {
        return executePending(run, null);
    }

    private RunDetail executePending(Run run, RunExecutionLock.LockToken lockToken) {
        try {
            for (Step step : List.copyOf(run.getSteps())) {
                if (run.getStatus() == RunStatus.CANCELLED) {
                    run.clearLease();
                    return toDetail(runRepository.save(run));
                }
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
            run.succeed(output);
            record(run.getId(), null, "RUN_SUCCEEDED", "任务执行成功");
        } catch (ExecutionTimeoutException exception) {
            run.timeout(exception.getMessage() == null ? "步骤执行超时" : exception.getMessage());
            metrics.runTimedOut();
            runRepository.save(run);
            record(run.getId(), null, "RUN_TIMED_OUT", run.getError());
        } catch (RuntimeException exception) {
            run.fail(exception.getMessage() == null ? exception.toString() : exception.getMessage());
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
        if (!executionLock.renew(lockToken, lease)) {
            throw new IllegalStateException("Run 执行锁已丢失，任务将等待恢复处理");
        }
        run.heartbeat(workerId, Instant.now().plus(lease));
        runRepository.save(run);
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
        auditEventRepository.save(new AuditEvent(
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
        return "tool=" + step.getName() + ";input=" + step.getInput();
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
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private void validateRuntimeLimits(CreateRunRequest request) {
        if (request.input() != null && request.input().length() > runtimeLimits.maxInputLength()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "INPUT_TOO_LARGE",
                    "任务输入超过允许的最大长度");
        }
        BigDecimal budget = request.budget() == null ? BigDecimal.ONE : request.budget();
        if (budget.compareTo(runtimeLimits.maxBudget()) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "BUDGET_EXCEEDED",
                    "任务预算超过运行上限");
        }
        long activeRuns = runRepository.countByTenantIdAndStatusIn(
                request.tenantId(), List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.WAITING_APPROVAL));
        if (activeRuns >= runtimeLimits.maxActiveRunsPerTenant()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TENANT_RUN_QUOTA_EXCEEDED",
                    "租户当前运行数已达到上限");
        }
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
        if (run.getPermissionsSnapshot() == null || run.getPermissionsSnapshot().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(run.getPermissionsSnapshot().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean sameCreateRequest(Run run, CreateRunRequest request, String toolName) {
        String modelName = valueOrDefault(request.modelName(), defaultModel);
        String promptVersion = valueOrDefault(request.promptVersion(), defaultPromptVersion);
        String policyVersion = valueOrDefault(request.policyVersion(), defaultPolicyVersion);
        return Objects.equals(run.getUserId(), request.userId())
                && Objects.equals(run.getTitle(), request.title())
                && Objects.equals(run.getInput(), request.input())
                && Objects.equals(run.getModelName(), modelName)
                && Objects.equals(run.getPromptVersion(), promptVersion)
                && Objects.equals(run.getPolicyVersion(), policyVersion)
                && run.getBudget().compareTo(request.budget() == null ? BigDecimal.ONE : request.budget()) == 0
                && run.getSteps().stream().anyMatch(step -> Objects.equals(step.getName(), toolName));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long countStatus(List<Run> runs, RunStatus status) {
        return runs.stream().filter(run -> run.getStatus() == status).count();
    }
}
