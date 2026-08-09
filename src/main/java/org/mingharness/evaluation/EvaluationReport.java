package org.mingharness.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harness_evaluation_reports")
public class EvaluationReport {

    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String name;
    private String modelName;
    private String promptVersion;
    private String policyVersion;
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private BigDecimal successRate;
    @Column(name = "baseline_report_id", length = 128)
    private String baselineReportId;
    @Column(name = "baseline_success_rate", precision = 10, scale = 4)
    private BigDecimal baselineSuccessRate;
    @Column(name = "success_rate_delta", precision = 10, scale = 4)
    private BigDecimal successRateDelta;
    @Column(name = "minimum_success_rate", precision = 10, scale = 4)
    private BigDecimal minimumSuccessRate;
    @Column(name = "gate_passed", nullable = false)
    private boolean gatePassed;
    @Column(columnDefinition = "text")
    private String details;
    private Instant createdAt;

    protected EvaluationReport() {
    }

    public EvaluationReport(String tenantId, String name, String modelName, String promptVersion,
                            String policyVersion, int totalCases, int passedCases, int failedCases,
                            BigDecimal successRate, String details) {
        this(tenantId, name, modelName, promptVersion, policyVersion, totalCases, passedCases,
                failedCases, successRate, details, null, null, null, null, true);
    }

    public EvaluationReport(String tenantId, String name, String modelName, String promptVersion,
                            String policyVersion, int totalCases, int passedCases, int failedCases,
                            BigDecimal successRate, String details, String baselineReportId,
                            BigDecimal baselineSuccessRate, BigDecimal successRateDelta,
                            BigDecimal minimumSuccessRate, boolean gatePassed) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.name = name;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.policyVersion = policyVersion;
        this.totalCases = totalCases;
        this.passedCases = passedCases;
        this.failedCases = failedCases;
        this.successRate = successRate;
        this.details = details;
        this.baselineReportId = baselineReportId;
        this.baselineSuccessRate = baselineSuccessRate;
        this.successRateDelta = successRateDelta;
        this.minimumSuccessRate = minimumSuccessRate;
        this.gatePassed = gatePassed;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public String getPolicyVersion() { return policyVersion; }
    public int getTotalCases() { return totalCases; }
    public int getPassedCases() { return passedCases; }
    public int getFailedCases() { return failedCases; }
    public BigDecimal getSuccessRate() { return successRate; }
    public String getBaselineReportId() { return baselineReportId; }
    public BigDecimal getBaselineSuccessRate() { return baselineSuccessRate; }
    public BigDecimal getSuccessRateDelta() { return successRateDelta; }
    public BigDecimal getMinimumSuccessRate() { return minimumSuccessRate; }
    public boolean isGatePassed() { return gatePassed; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
