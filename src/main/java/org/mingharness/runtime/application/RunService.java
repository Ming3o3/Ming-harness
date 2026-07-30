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
import org.mingharness.runtime.repository.RunRepository;
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

    public RunService(RunRepository runRepository,
                      AuditEventRepository auditEventRepository,
                      ToolRegistry toolRegistry) {
        this.runRepository = runRepository;
        this.auditEventRepository = auditEventRepository;
        this.toolRegistry = toolRegistry;
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
                request.budget() == null ? BigDecimal.ONE : request.budget()
        );
        run.addStep(new Step(1, StepType.TOOL, toolName, request.input()));
        Run saved = runRepository.save(run);
        record(saved.getId(), null, "RUN_CREATED", "创建执行任务");
        return toSummary(saved);
    }

    @Transactional
    public RunDetail start(String runId) {
        Run run = getRun(runId);
        if (run.getStatus() == RunStatus.SUCCEEDED) {
            return toDetail(run);
        }
        if (run.getStatus() != RunStatus.QUEUED) {
            throw new BusinessException(HttpStatus.CONFLICT, "RUN_NOT_STARTABLE", "任务当前状态不能启动: " + run.getStatus());
        }

        run.start();
        runRepository.save(run);
        record(run.getId(), null, "RUN_STARTED", "开始执行任务");

        try {
            for (Step step : run.getSteps()) {
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
            run.fail(exception.getMessage() == null ? "执行失败" : exception.getMessage());
            runRepository.save(run);
            record(run.getId(), null, "RUN_FAILED", run.getError());
        }
        return toDetail(runRepository.save(run));
    }

    @Transactional(readOnly = true)
    public RunDetail getDetail(String runId) {
        return toDetail(getRun(runId));
    }

    @Transactional(readOnly = true)
    public List<RunSummary> list() {
        return runRepository.findTop50ByOrderByCreatedAtDesc().stream().map(this::toSummary).toList();
    }

    @Transactional
    public void cancel(String runId) {
        Run run = getRun(runId);
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

    private void executeStep(Run run, Step step) {
        if (step.getStatus() == StepStatus.SUCCEEDED) {
            return;
        }
        step.start();
        runRepository.save(run);
        record(run.getId(), step.getId(), "STEP_STARTED", "开始执行步骤: " + step.getName());
        try {
            HarnessTool tool = toolRegistry.get(step.getName());
            String output = tool.execute(step.getInput());
            step.succeed(output);
            record(run.getId(), step.getId(), "STEP_SUCCEEDED", "步骤执行成功");
        } catch (Exception exception) {
            step.fail(exception.getMessage() == null ? "步骤执行失败" : exception.getMessage());
            record(run.getId(), step.getId(), "STEP_FAILED", step.getError());
            throw exception;
        }
    }

    private Run getRun(String runId) {
        return runRepository.findById(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
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
                        step.getStartedAt(), step.getFinishedAt()
                )).toList()
        );
    }

    private RunSummary toSummary(Run run) {
        return new RunSummary(
                run.getId(), run.getTenantId(), run.getUserId(), run.getTitle(), run.getInput(),
                run.getOutput(), run.getError(), run.getStatus(), run.getBudget(), run.getCreatedAt(),
                run.getUpdatedAt(), run.getSteps().size()
        );
    }
}
