package org.mingharness.runtime.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "harness_runs")
public class Run {

    @Id
    private String id;
    private String tenantId;
    private String userId;
    private String title;
    private String modelName;
    private String promptVersion;
    private String policyVersion;
    @Lob
    @Column(name = "input_data")
    private String input;
    @Lob
    @Column(name = "output_data")
    private String output;
    @Lob
    private String error;
    @Enumerated(EnumType.STRING)
    private RunStatus status;
    private BigDecimal budget;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Step> steps = new ArrayList<>();

    protected Run() {
    }

    public Run(String tenantId, String userId, String title, String input, BigDecimal budget,
               String modelName, String promptVersion, String policyVersion) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.title = title;
        this.input = input;
        this.budget = budget;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.policyVersion = policyVersion;
        this.status = RunStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void start() {
        requireStatus(RunStatus.QUEUED);
        this.status = RunStatus.RUNNING;
        touch();
    }

    public void succeed(String output) {
        requireStatus(RunStatus.RUNNING);
        this.output = output;
        this.status = RunStatus.SUCCEEDED;
        touch();
    }

    public void fail(String error) {
        if (status == RunStatus.SUCCEEDED || status == RunStatus.CANCELLED) {
            return;
        }
        this.error = error;
        this.status = RunStatus.FAILED;
        touch();
    }

    public void cancel() {
        if (status == RunStatus.SUCCEEDED || status == RunStatus.FAILED || status == RunStatus.CANCELLED) {
            return;
        }
        this.status = RunStatus.CANCELLED;
        touch();
    }

    public void waitApproval() {
        requireStatus(RunStatus.RUNNING);
        this.status = RunStatus.WAITING_APPROVAL;
        touch();
    }

    public void resumeAfterApproval() {
        requireStatus(RunStatus.WAITING_APPROVAL);
        this.status = RunStatus.RUNNING;
        touch();
    }

    public void addStep(Step step) {
        step.attachTo(this);
        this.steps.add(step);
        touch();
    }

    private void requireStatus(RunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Run 状态不允许执行当前操作: " + status);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public String getPolicyVersion() { return policyVersion; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public RunStatus getStatus() { return status; }
    public BigDecimal getBudget() { return budget; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<Step> getSteps() { return steps; }
}
