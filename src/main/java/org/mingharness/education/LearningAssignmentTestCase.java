package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 教师为编程作业配置的单个行为测试用例；定义一旦进入 Run 快照便不可追溯修改。 */
@Entity
@Table(name = "harness_learning_assignment_test_cases", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_assignment_test_case_key",
        columnNames = {"tenant_id", "learning_assignment_id", "case_key"}))
public class LearningAssignmentTestCase {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(name = "learning_assignment_id", nullable = false, length = 255)
    private String learningAssignmentId;
    @Column(name = "case_key", nullable = false, length = 64)
    private String caseKey;
    /** 可选的知识点标注；为空时行为证据回退到作业目标知识点。 */
    @Column(name = "concept_key", length = 255)
    private String conceptKey;
    @Column(length = 255)
    private String name;
    @Column(name = "input_data", nullable = false, columnDefinition = "text")
    private String inputData;
    @Column(name = "expected_output", nullable = false, columnDefinition = "text")
    private String expectedOutput;
    @Column(nullable = false)
    private boolean hidden;
    @Column(nullable = false)
    private double weight;
    @Column(nullable = false)
    private int sequence;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearningAssignmentTestCase() {
    }

    public LearningAssignmentTestCase(String tenantId, String learningAssignmentId,
                                      String caseKey, String name, String inputData,
                                      String expectedOutput, boolean hidden, double weight,
                                      int sequence) {
        this(tenantId, learningAssignmentId, caseKey, null, name, inputData, expectedOutput,
                hidden, weight, sequence);
    }

    public LearningAssignmentTestCase(String tenantId, String learningAssignmentId,
                                      String caseKey, String conceptKey, String name,
                                      String inputData, String expectedOutput, boolean hidden,
                                      double weight, int sequence) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learningAssignmentId = required(learningAssignmentId, "learningAssignmentId");
        this.caseKey = required(caseKey, "caseKey");
        if (this.caseKey.length() > 64) throw new IllegalArgumentException("caseKey 不能超过 64 个字符");
        this.conceptKey = optional(conceptKey);
        this.name = optional(name);
        this.inputData = inputData == null ? "" : inputData;
        this.expectedOutput = requiredText(expectedOutput, "expectedOutput");
        this.hidden = hidden;
        this.weight = normalizeWeight(weight);
        this.sequence = Math.max(0, sequence);
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void disable(Instant observedAt) {
        enabled = false;
        updatedAt = observedAt == null ? Instant.now() : observedAt;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String requiredText(String value, String name) {
        String normalized = value == null ? "" : value;
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String optional(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static double normalizeWeight(double value) {
        return !Double.isFinite(value) || value <= 0.0 ? 1.0 : Math.min(100.0, value);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public String getCaseKey() { return caseKey; }
    public String getConceptKey() { return conceptKey; }
    public String getName() { return name; }
    public String getInputData() { return inputData; }
    public String getExpectedOutput() { return expectedOutput; }
    public boolean isHidden() { return hidden; }
    public double getWeight() { return weight; }
    public int getSequence() { return sequence; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
