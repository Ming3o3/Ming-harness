package org.mingharness.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.mingharness.runtime.domain.RunScenario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harness_evaluation_cases")
public class EvaluationCase {

    @Id
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "owner_user_id", nullable = false, length = 128)
    private String ownerUserId;
    @Column(name = "source_run_id", length = 128)
    private String sourceRunId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String input;
    @Column(name = "tool_name", length = 128)
    private String toolName;
    @Column(name = "expected_contains", columnDefinition = "text")
    private String expectedContains;
    private BigDecimal budget;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunScenario scenario;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EvaluationCase() {
    }

    public EvaluationCase(String tenantId, String ownerUserId, String sourceRunId, String name,
                          String input, String toolName, String expectedContains, BigDecimal budget,
                          RunScenario scenario) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.ownerUserId = ownerUserId;
        this.sourceRunId = sourceRunId;
        this.name = name;
        this.input = input;
        this.toolName = toolName;
        this.expectedContains = expectedContains;
        this.budget = budget;
        this.scenario = scenario == null ? RunScenario.UNCLASSIFIED : scenario;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getSourceRunId() { return sourceRunId; }
    public String getName() { return name; }
    public String getInput() { return input; }
    public String getToolName() { return toolName; }
    public String getExpectedContains() { return expectedContains; }
    public BigDecimal getBudget() { return budget; }
    public RunScenario getScenario() { return scenario == null ? RunScenario.UNCLASSIFIED : scenario; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
