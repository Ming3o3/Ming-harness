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
import org.mingharness.tool.ToolRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RunService {

    private final RunRepository runRepository;
    private final AuditEventRepository auditEventRepository;
    private final ToolRegistry toolRegistry;
    private final ModelGateway modelGateway;
    private final String defaultModel;
    private final String defaultPromptVersion;
    private final String defaultPolicyVersion;

    public RunService(RunRepository runRepository,
                      AuditEventRepository auditEventRepository,
                      ToolRegistry toolRegistry,
                      ModelGateway modelGateway,
                      @Value("${harness.model.name:demo-model}") String defaultModel,
                      @Value("${harness.prompt.version:prompt-v1}") String defaultPromptVersion,
                      @Value("${harness.policy.version:policy-v1}") String defaultPolicyVersion) {
        this.runRepository = runRepository;
        this.auditEventRepository = auditEventRepository;
        this.toolRegistry = toolRegistry;
        this.modelGateway = modelGateway;
        this.defaultModel = defaultModel;
        this.defaultPromptVersion = defaultPromptVersion;
        this.defaultPolicyVersion = defaultPolicyVersion;
    }

    @Transactional
    public RunSummary create(CreateRunRequest request) {
        String toolName = request.toolName() == null || request.toolName().isBlank()
                ? "demo.echo" : request.toolName();
        toolRegistry.get(toolName);

        Run run = new Run(
                request.tenantId(),
                request.userId(),
                request.title(),
                request.input(),
                request.budget() == null ? BigDecimal.ONE : request.budget(),
                valueOrDefault(request.modelName(), defaultModel),
                valueOrDefault(request.promptVersion(), defaultPromptVersion),
                valueOrDefault(request.policyVersion(), defaultPolicyVersion)
        );
        run.addStep(new Step(1, StepType.MODEL, "model.complete", request.input()));
        run.addStep(new Step(2, StepType.TOOL, toolName, request.input()));
        Run saved = runRepository.save(run);
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

        return executePending(run);
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
                runs.stream().flatMap(run -> run.getSteps().stream()).mapToLong(Step::getOutputTokens).sum()
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
        record(run.getId(), step.getId(), "APPROVAL_APPROVED", "人工审批通过");
        return executePending(run);
    }

    @Transactional
    public RunDetail reject(String runId, String tenantId, String reason) {
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
        record(run.getId(), step.getId(), "APPROVAL_REJECTED", rejectReason);
        record(run.getId(), null, "RUN_FAILED", rejectReason);
        return toDetail(run);
    }

    public void assertTenant(String runId, String tenantId) {
        assertTenant(getRun(runId), tenantId);
    }

    private void executeStep(Run run, Step step) {
        if (step.getStatus() == StepStatus.SUCCEEDED) {
            return;
        }
        step.start();
        runRepository.save(run);
        record(run.getId(), step.getId(), "STEP_STARTED", "开始执行步骤: " + step.getName());
        try {
            if (step.getType() == StepType.MODEL) {
                ModelResponse response = modelGateway.complete(new ModelRequest(
                        step.getInput(), run.getModelName(), run.getPromptVersion()
                ));
                step.succeed(response.content(), response.inputTokens(), response.outputTokens());
            } else {
                HarnessTool tool = toolRegistry.get(step.getName());
                String output = tool.execute(step.getInput());
                step.succeed(output);
            }
            record(run.getId(), step.getId(), "STEP_SUCCEEDED", "步骤执行成功");
        } catch (Exception exception) {
            step.fail(exception.getMessage() == null ? "步骤执行失败" : exception.getMessage());
            record(run.getId(), step.getId(), "STEP_FAILED", step.getError());
            throw exception;
        }
    }

    private RunDetail executePending(Run run) {
        try {
            for (Step step : List.copyOf(run.getSteps())) {
                if (step.getStatus() == StepStatus.SUCCEEDED) {
                    continue;
                }
                if (step.getType() == StepType.TOOL) {
                    HarnessTool tool = toolRegistry.get(step.getName());
                    if (tool.definition().requiresApproval() && !step.isApprovalGranted()) {
                        step.requestApproval();
                        run.waitApproval();
                        runRepository.save(run);
                        record(run.getId(), step.getId(), "APPROVAL_REQUESTED", "高风险工具等待人工审批");
                        return toDetail(run);
                    }
                }
                executeStep(run, step);
            }
            String output = run.getSteps().stream()
                    .filter(step -> step.getStatus() == StepStatus.SUCCEEDED)
                    .reduce((left, right) -> right)
                    .map(Step::getOutput)
                    .orElse("");
            run.succeed(output);
            record(run.getId(), null, "RUN_SUCCEEDED", "任务执行成功");
        } catch (RuntimeException exception) {
            run.fail(exception.getMessage() == null ? exception.toString() : exception.getMessage());
            runRepository.save(run);
            record(run.getId(), null, "RUN_FAILED", run.getError());
        }
        return toDetail(runRepository.save(run));
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
        auditEventRepository.save(new AuditEvent(runId, stepId, eventType, message));
    }

    private RunDetail toDetail(Run run) {
        return new RunDetail(
                toSummary(run),
                run.getSteps().stream().map(step -> new StepView(
                        step.getId(), step.getSequence(), step.getType(), step.getStatus(), step.getName(),
                        step.getInput(), step.getOutput(), step.getError(), step.getAttempt(),
                        step.getInputTokens(), step.getOutputTokens(),
                        step.getStartedAt(), step.getFinishedAt()
                )).toList()
        );
    }

    private RunSummary toSummary(Run run) {
        return new RunSummary(
                run.getId(), run.getTenantId(), run.getUserId(), run.getTitle(), run.getModelName(),
                run.getPromptVersion(), run.getPolicyVersion(), run.getInput(),
                run.getOutput(), run.getError(), run.getStatus(), run.getBudget(), run.getCreatedAt(),
                run.getUpdatedAt(), run.getSteps().size()
        );
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long countStatus(List<Run> runs, RunStatus status) {
        return runs.stream().filter(run -> run.getStatus() == status).count();
    }
}
