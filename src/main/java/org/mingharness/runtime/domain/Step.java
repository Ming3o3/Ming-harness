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
    @Column(columnDefinition = "text")
    private String error;
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

    public void fail(String error) {
        this.error = error;
        this.status = StepStatus.FAILED;
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

    public String getId() { return id; }
    public String getSpanId() { return spanId; }
    public Run getRun() { return run; }
    public int getSequence() { return sequence; }
    public StepType getType() { return type; }
    public StepStatus getStatus() { return status; }
    public String getName() { return name; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public int getAttempt() { return attempt; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public boolean isApprovalGranted() { return approvalGranted; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public long getDurationMs() { return durationMs; }
    public BigDecimal getCost() { return cost; }

    private long elapsedMs() {
        return startedAt == null || finishedAt == null
                ? 0 : java.time.Duration.between(startedAt, finishedAt).toMillis();
    }
}
