package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Column(name = "learning_assignment_id", length = 128)
    private String learningAssignmentId;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 32)
    private AssessmentAttemptType assessmentType;
    @Column(name = "review_plan_id", length = 255)
    private String reviewPlanId;
    @Column(name = "evidence_source", nullable = false, length = 32)
    private String evidenceSource;
    @Column(name = "evidence_text", length = 4000)
    private String evidenceText;
    /** 模型评价必须锚定到本轮学习者输入中的原话，便于回放时核对证据归因。 */
    @Column(name = "learner_evidence_quote", length = 2000)
    private String learnerEvidenceQuote;
    @Column(length = 1000)
    private String feedback;
    @Column(name = "retrieval_evidence_json", columnDefinition = "text")
    private String retrievalEvidenceJson;
    @Column(nullable = false)
    private Instant createdAt;

    protected AssessmentAttempt() {
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, String feedback) {
        this(tenantId, userId, runId, stepId, learningGoalId, learnerProfileId, conceptKey,
                correct, observedMastery, masteryBefore, masteryAfter,
                AssessmentAttemptType.FORMATIVE, null, "MODEL_TOOL", null, feedback, null);
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, String evidenceSource, String evidenceText,
                             String feedback) {
        this(tenantId, userId, runId, stepId, learningGoalId, learnerProfileId, conceptKey,
                correct, observedMastery, masteryBefore, masteryAfter,
                AssessmentAttemptType.FORMATIVE, null, evidenceSource, evidenceText, feedback, null);
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, AssessmentAttemptType assessmentType,
                             String reviewPlanId, String evidenceSource, String evidenceText,
                             String feedback) {
        this(tenantId, userId, runId, stepId, learningGoalId, learnerProfileId, conceptKey,
                correct, observedMastery, masteryBefore, masteryAfter, assessmentType,
                reviewPlanId, evidenceSource, evidenceText, feedback, null);
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, AssessmentAttemptType assessmentType,
                             String reviewPlanId, String evidenceSource, String evidenceText,
                             String feedback, String learningAssignmentId) {
        this(tenantId, userId, runId, stepId, learningGoalId, learnerProfileId, conceptKey,
                correct, observedMastery, masteryBefore, masteryAfter, assessmentType,
                reviewPlanId, evidenceSource, evidenceText, feedback, learningAssignmentId, null);
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, AssessmentAttemptType assessmentType,
                             String reviewPlanId, String evidenceSource, String evidenceText,
                             String feedback, String learningAssignmentId,
                             String retrievalEvidenceJson) {
        this(tenantId, userId, runId, stepId, learningGoalId, learnerProfileId, conceptKey,
                correct, observedMastery, masteryBefore, masteryAfter, assessmentType,
                reviewPlanId, evidenceSource, evidenceText, feedback, learningAssignmentId,
                retrievalEvidenceJson, null);
    }

    public AssessmentAttempt(String tenantId, String userId, String runId, String stepId,
                             String learningGoalId, String learnerProfileId, String conceptKey,
                             boolean correct, double observedMastery, double masteryBefore,
                             double masteryAfter, AssessmentAttemptType assessmentType,
                             String reviewPlanId, String evidenceSource, String evidenceText,
                             String feedback, String learningAssignmentId,
                             String retrievalEvidenceJson, String learnerEvidenceQuote) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.runId = required(runId, "runId");
        this.stepId = required(stepId, "stepId");
        this.learningGoalId = required(learningGoalId, "learningGoalId");
        this.learningAssignmentId = learningAssignmentId == null || learningAssignmentId.isBlank()
                ? null : learningAssignmentId.trim();
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.correct = correct;
        this.observedMastery = clamp(observedMastery);
        this.masteryBefore = clamp(masteryBefore);
        this.masteryAfter = clamp(masteryAfter);
        this.assessmentType = assessmentType == null ? AssessmentAttemptType.FORMATIVE : assessmentType;
        this.reviewPlanId = reviewPlanId == null || reviewPlanId.isBlank() ? null : reviewPlanId.trim();
        this.evidenceSource = required(evidenceSource, "evidenceSource");
        this.evidenceText = evidenceText == null || evidenceText.isBlank() ? null : evidenceText.trim();
        this.learnerEvidenceQuote = learnerEvidenceQuote == null || learnerEvidenceQuote.isBlank()
                ? null : learnerEvidenceQuote.trim();
        this.feedback = feedback == null || feedback.isBlank() ? null : feedback.trim();
        this.retrievalEvidenceJson = retrievalEvidenceJson == null || retrievalEvidenceJson.isBlank()
                ? "[]" : retrievalEvidenceJson.trim();
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
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getConceptKey() { return conceptKey; }
    public boolean isCorrect() { return correct; }
    public double getObservedMastery() { return observedMastery; }
    public double getMasteryBefore() { return masteryBefore; }
    public double getMasteryAfter() { return masteryAfter; }
    public AssessmentAttemptType getAssessmentType() { return assessmentType; }
    public String getReviewPlanId() { return reviewPlanId; }
    public String getEvidenceSource() { return evidenceSource; }
    public String getEvidenceText() { return evidenceText; }
    public String getLearnerEvidenceQuote() { return learnerEvidenceQuote; }
    public String getFeedback() { return feedback; }
    public String getRetrievalEvidenceJson() { return retrievalEvidenceJson; }
    public Instant getCreatedAt() { return createdAt; }
}
