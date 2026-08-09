package org.mingharness.runtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harness_steps")
public class Step {

    @Id
    private String id;
    private String spanId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;
    @Column(name = "step_sequence")
    private int sequence;
    @Enumerated(EnumType.STRING)
    private StepType type;
    @Enumerated(EnumType.STRING)
    private StepStatus status;
    private String name;
    @Column(name = "input_data", columnDefinition = "text")
    private String input;
    @Column(name = "output_data", columnDefinition = "text")
    private String output;
    /** 模型步骤使用的授权检索来源快照；正文已按上下文治理边界脱敏。 */
    @Column(name = "context_evidence_data", columnDefinition = "text")
    private String contextEvidenceJson;
    @Column(columnDefinition = "text")
    private String error;
    /**
     * 当前 Tool Call 复用了哪个已成功步骤的结果。保留来源可使模型消息仍能按原调用顺序配对，
     * 同时避免工具因模型重复规划而再次执行。
     */
    @Column(name = "replay_source_step_id", length = 255)
    private String replaySourceStepId;
    private int attempt;
    private int inputTokens;
    private int outputTokens;
    private boolean approvalGranted;
    private Instant startedAt;
    private Instant finishedAt;
    private long durationMs;
    private BigDecimal cost = BigDecimal.ZERO;

    protected Step() {
    }

    public Step(int sequence, StepType type, String name, String input) {
        this.id = UUID.randomUUID().toString();
        this.spanId = UUID.randomUUID().toString();
        this.sequence = sequence;
        this.type = type;
        this.name = name;
        this.input = input;
        this.status = StepStatus.QUEUED;
    }

    void attachTo(Run run) {
        this.run = run;
    }

    public void start() {
        if (status != StepStatus.QUEUED) {
            throw new IllegalStateException("Step 已经执行过，不能重复启动: " + status);
        }
        this.status = StepStatus.RUNNING;
        this.attempt++;
        this.startedAt = Instant.now();
    }

    public void succeed(String output) {
        succeed(output, 0, 0);
    }

    public void succeed(String output, int inputTokens, int outputTokens) {
        succeed(output, inputTokens, outputTokens, BigDecimal.ZERO);
    }

    public void succeed(String output, int inputTokens, int outputTokens, BigDecimal cost) {
        if (status != StepStatus.RUNNING) {
            throw new IllegalStateException("Step 不在执行中: " + status);
        }
        this.output = output;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cost = cost == null ? BigDecimal.ZERO : cost;
        this.status = StepStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.durationMs = elapsedMs();
    }

    /**
     * 标记为复用结果的工具步骤。复用步骤不会调用外部工具，因此不计入工具尝试次数或成本。
     */
    public void replayFrom(Step source) {
        if (status != StepStatus.QUEUED) {
            throw new IllegalStateException("只有排队中的步骤可以复用工具结果: " + status);
        }
        if (source == null || source.getStatus() != StepStatus.SUCCEEDED) {
            throw new IllegalStateException("只能复用已成功工具步骤的结果");
        }
        this.replaySourceStepId = source.getId();
        this.output = source.getOutput();
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.cost = BigDecimal.ZERO;
        this.finishedAt = Instant.now();
        this.durationMs = 0;
        this.status = StepStatus.SUCCEEDED;
    }

    /**
     * 同一模型响应内的重复调用会等待首个工具步骤完成后再复用其结果。
     */
    public void queueReplayFrom(String sourceStepId) {
        if (status != StepStatus.QUEUED) {
            throw new IllegalStateException("只有排队中的步骤可以等待复用工具结果: " + status);
        }
        if (sourceStepId == null || sourceStepId.isBlank()) {
            throw new IllegalArgumentException("复用来源步骤不能为空");
        }
        this.replaySourceStepId = sourceStepId;
    }

    /** 外部模型仍在生成时保存最新输出快照，最终状态仍须通过 {@link #succeed} 写入。 */
    public void updateRunningOutput(String output) {
        if (status != StepStatus.RUNNING) {
            return;
        }
        this.output = output == null ? "" : output;
    }

    public void fail(String error) {
        this.error = error;
        this.status = StepStatus.FAILED;
        this.finishedAt = Instant.now();
        this.durationMs = elapsedMs();
    }

    /** 记录人工拒绝的工具结果，保留拒绝原因供 Agent 下一轮重新规划。 */
    public void reject(String reason, String toolResult) {
        if (status != StepStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("只有待审批步骤可以被拒绝: " + status);
        }
        this.error = reason;
        this.output = toolResult == null ? "人工审批已拒绝该工具调用" : toolResult;
        this.status = StepStatus.REJECTED;
        this.finishedAt = Instant.now();
        this.durationMs = elapsedMs();
    }

    public void cancel(String reason) {
        if (status == StepStatus.SUCCEEDED || status == StepStatus.REJECTED || status == StepStatus.FAILED
                || status == StepStatus.CANCELLED || status == StepStatus.TIMED_OUT) {
            return;
        }
        this.error = reason;
        this.status = StepStatus.CANCELLED;
        this.finishedAt = Instant.now();
        this.durationMs = elapsedMs();
    }

    public void timeout(String error) {
        this.error = error;
        this.status = StepStatus.TIMED_OUT;
        this.finishedAt = Instant.now();
        this.durationMs = elapsedMs();
    }

    public void requestApproval() {
        if (status != StepStatus.QUEUED) {
            throw new IllegalStateException("只有排队中的步骤可以申请审批: " + status);
        }
        this.status = StepStatus.WAITING_APPROVAL;
    }

    public void approve() {
        if (status != StepStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("步骤当前不在等待审批状态: " + status);
        }
        this.status = StepStatus.QUEUED;
        this.approvalGranted = true;
    }

    public void retry() {
        if (status != StepStatus.FAILED && status != StepStatus.TIMED_OUT) {
            return;
        }
        this.status = StepStatus.QUEUED;
        this.error = null;
        this.startedAt = null;
        this.finishedAt = null;
        this.approvalGranted = false;
    }

    /**
     * 只读工具在同一次执行中的瞬态重试。
     * 保留已经完成的审批快照，避免自动重试过程中意外扩大或撤销授权语义。
     */
    public void retryAutomatically() {
        if (status != StepStatus.FAILED && status != StepStatus.TIMED_OUT) {
            return;
        }
        this.status = StepStatus.QUEUED;
        this.error = null;
        this.startedAt = null;
        this.finishedAt = null;
    }

    /**
     * Worker 在事务性基础设施故障后回滚当前步骤，交给 Rabbit 重试；不能把 RUNNING 步骤留给下一次消费。
     */
    public void requeueAfterInfrastructureFailure() {
        if (status != StepStatus.RUNNING) {
            return;
        }
        this.status = StepStatus.QUEUED;
        this.error = null;
        this.startedAt = null;
        this.finishedAt = null;
        this.durationMs = 0;
    }

    public String getId() { return id; }
    public String getSpanId() { return spanId; }
    public Run getRun() { return run; }
    public int getSequence() { return sequence; }
    public StepType getType() { return type; }
    public StepStatus getStatus() { return status; }
    public String getName() { return name; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public String getContextEvidenceJson() { return contextEvidenceJson; }

    public void setContextEvidenceJson(String contextEvidenceJson) {
        this.contextEvidenceJson = contextEvidenceJson == null ? "[]" : contextEvidenceJson;
    }
    public String getError() { return error; }
    public int getAttempt() { return attempt; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public boolean isApprovalGranted() { return approvalGranted; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public long getDurationMs() { return durationMs; }
    public BigDecimal getCost() { return cost; }
    public String getReplaySourceStepId() { return replaySourceStepId; }
    public boolean isReplayPending() {
        return status == StepStatus.QUEUED && replaySourceStepId != null && !replaySourceStepId.isBlank();
    }

    private long elapsedMs() {
        return startedAt == null || finishedAt == null
                ? 0 : java.time.Duration.between(startedAt, finishedAt).toMillis();
    }
}
