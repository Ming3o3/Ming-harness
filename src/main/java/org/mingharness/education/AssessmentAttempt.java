package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 绑定到学习目标和 Run Step 的形成性测评记录。 */
@Entity
@Table(name = "harness_assessment_attempts")
public class AssessmentAttempt {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 255)
    private String runId;
    @Column(nullable = false, length = 255)
    private String stepId;
    @Column(nullable = false, length = 255)
    private String learningGoalId;
    @Column(nullable = false, length = 255)
    private String learnerProfileId;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Column(nullable = false)
    private boolean correct;
    @Column(nullable = false)
    private double observedMastery;
    @Column(nullable = false)
    private double masteryBefore;
    @Column(nullable = false)
    private double masteryAfter;
    @Column(length = 1000)
    private String feedback;
    @Column(nullable = false)
    private Instant createdAt;

    protected AssessmentAttempt() {
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, String feedback) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.runId = required(runId, "runId");
        this.stepId = required(stepId, "stepId");
        this.learningGoalId = required(learningGoalId, "learningGoalId");
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.correct = correct;
        this.observedMastery = clamp(observedMastery);
        this.masteryBefore = clamp(masteryBefore);
        this.masteryAfter = clamp(masteryAfter);
        this.feedback = feedback == null || feedback.isBlank() ? null : feedback.trim();
        this.createdAt = Instant.now();
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getRunId() { return runId; }
    public String getStepId() { return stepId; }
    public String getLearningGoalId() { return learningGoalId; }
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getConceptKey() { return conceptKey; }
    public boolean isCorrect() { return correct; }
    public double getObservedMastery() { return observedMastery; }
    public double getMasteryBefore() { return masteryBefore; }
    public double getMasteryAfter() { return masteryAfter; }
    public String getFeedback() { return feedback; }
    public Instant getCreatedAt() { return createdAt; }
}
