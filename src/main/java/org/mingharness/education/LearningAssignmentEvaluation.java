package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 一次教师审核的不可变量规事实。返工和最终确认分别留下记录，
 * 不用后一次审核覆盖前一次证据。
 */
@Entity
@Table(name = "harness_learning_assignment_evaluations")
public class LearningAssignmentEvaluation {

    public static final String CURRENT_RUBRIC_VERSION = "education-v1";

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String learningAssignmentId;
    @Column(length = 255)
    private String courseId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Column(nullable = false, length = 255)
    private String evaluatorUserId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAssignmentEvaluationDecision decision;
    @Column(nullable = false, length = 64)
    private String rubricVersion;
    @Column(nullable = false)
    private int contentCorrectnessScore;
    @Column(nullable = false)
    private int evidenceQualityScore;
    @Column(nullable = false)
    private int transferReadinessScore;
    @Column(columnDefinition = "text")
    private String note;
    @Column(nullable = false)
    private Instant createdAt;

    protected LearningAssignmentEvaluation() {
    }

    public LearningAssignmentEvaluation(String tenantId, String learningAssignmentId,
                                        String courseId, String learnerUserId,
                                        String evaluatorUserId,
                                        LearningAssignmentEvaluationDecision decision,
                                        int contentCorrectnessScore, int evidenceQualityScore,
                                        int transferReadinessScore, String note, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learningAssignmentId = required(learningAssignmentId, "learningAssignmentId");
        this.courseId = optional(courseId);
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.evaluatorUserId = required(evaluatorUserId, "evaluatorUserId");
        if (decision == null) throw new IllegalArgumentException("decision 不能为空");
        this.decision = decision;
        this.rubricVersion = CURRENT_RUBRIC_VERSION;
        this.contentCorrectnessScore = score(contentCorrectnessScore, "contentCorrectnessScore");
        this.evidenceQualityScore = score(evidenceQualityScore, "evidenceQualityScore");
        this.transferReadinessScore = score(transferReadinessScore, "transferReadinessScore");
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static int score(int value, String name) {
        if (value < 1 || value > 5) throw new IllegalArgumentException(name + " 必须在 1 到 5 之间");
        return value;
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static String optional(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public String getCourseId() { return courseId; }
    public String getLearnerUserId() { return learnerUserId; }
    public String getEvaluatorUserId() { return evaluatorUserId; }
    public LearningAssignmentEvaluationDecision getDecision() { return decision; }
    public String getRubricVersion() { return rubricVersion; }
    public int getContentCorrectnessScore() { return contentCorrectnessScore; }
    public int getEvidenceQualityScore() { return evidenceQualityScore; }
    public int getTransferReadinessScore() { return transferReadinessScore; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
