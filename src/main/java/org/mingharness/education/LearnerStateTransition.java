package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 学习者状态的一次不可变转移事实。
 *
 * <p>该表不替代掌握度当前值，而是保存“为什么从 before 变成 after”，供教师复核、
 * 实验导出和状态模型校准使用。</p>
 */
@Entity
@Table(name = "harness_learner_state_transitions")
public class LearnerStateTransition {

    public static final String STATE_MODEL_VERSION = "STATE_TRANSITION_V1";

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Column(nullable = false, length = 255)
    private String learnerProfileId;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Column(length = 255)
    private String runId;
    @Column(nullable = false)
    private double beforeMastery;
    @Column(nullable = false)
    private double afterMastery;
    @Column(nullable = false)
    private double beforeEffectiveMastery;
    @Column(nullable = false)
    private double afterEffectiveMastery;
    @Column(nullable = false)
    private double beforeRetentionScore;
    @Column(nullable = false)
    private double afterRetentionScore;
    @Column(nullable = false)
    private int beforeAttempts;
    @Column(nullable = false)
    private int afterAttempts;
    @Column(nullable = false)
    private int beforeCorrectAttempts;
    @Column(nullable = false)
    private int afterCorrectAttempts;
    @Column(nullable = false, length = 64)
    private String evidenceSource;
    @Column(nullable = false, length = 32)
    private String assessmentType;
    @Column(length = 64)
    private String diagnosticCategory;
    @Column(length = 4000)
    private String evidenceText;
    private Double behaviorTestPassRate;
    @Column(nullable = false)
    private int difficultyLevel;
    @Column(nullable = false)
    private double evidenceWeight;
    @Column(nullable = false)
    private boolean hintUsed;
    @Column(nullable = false)
    private boolean independentEvidence;
    @Column(nullable = false, length = 64)
    private String stateModelVersion;
    @Column(nullable = false, length = 64)
    private String retentionModelVersion;
    @Column(nullable = false)
    private Instant createdAt;

    protected LearnerStateTransition() {
    }

    public LearnerStateTransition(String tenantId, String learnerUserId, String learnerProfileId,
                                  String conceptKey, LearnerMastery before, LearnerMastery after,
                                  LearnerStateTransitionContext context, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.runId = optional(context == null ? null : context.runId());
        Instant eventTime = createdAt == null ? Instant.now() : createdAt;
        this.beforeMastery = before == null ? 0.0 : before.getMasteryScore();
        this.afterMastery = after == null ? 0.0 : after.getMasteryScore();
        this.beforeEffectiveMastery = before == null ? 0.0 : before.effectiveMasteryAt(eventTime);
        this.afterEffectiveMastery = after == null ? 0.0 : after.effectiveMasteryAt(eventTime);
        this.beforeRetentionScore = before == null ? 1.0 : before.retentionScoreAt(eventTime);
        this.afterRetentionScore = after == null ? 1.0 : after.retentionScoreAt(eventTime);
        this.beforeAttempts = before == null ? 0 : before.getAttempts();
        this.afterAttempts = after == null ? 0 : after.getAttempts();
        this.beforeCorrectAttempts = before == null ? 0 : before.getCorrectAttempts();
        this.afterCorrectAttempts = after == null ? 0 : after.getCorrectAttempts();
        LearnerStateTransitionContext value = context == null
                ? LearnerStateTransitionContext.observation(null, null, null, null, null) : context;
        this.evidenceSource = required(value.effectiveEvidenceSource(), "evidenceSource");
        this.assessmentType = required(value.effectiveAssessmentType(), "assessmentType");
        this.diagnosticCategory = optional(value.diagnosticCategory());
        this.evidenceText = optional(value.evidenceText());
        this.behaviorTestPassRate = value.behaviorTestPassRate();
        this.difficultyLevel = value.difficultyLevel();
        this.evidenceWeight = value.evidenceWeight();
        this.hintUsed = value.hintUsed();
        this.independentEvidence = value.independentEvidence();
        this.stateModelVersion = STATE_MODEL_VERSION;
        this.retentionModelVersion = LearnerRetentionModel.VERSION;
        this.createdAt = eventTime;
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
    public String getLearnerUserId() { return learnerUserId; }
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getConceptKey() { return conceptKey; }
    public String getRunId() { return runId; }
    public double getBeforeMastery() { return beforeMastery; }
    public double getAfterMastery() { return afterMastery; }
    public double getBeforeEffectiveMastery() { return beforeEffectiveMastery; }
    public double getAfterEffectiveMastery() { return afterEffectiveMastery; }
    public double getBeforeRetentionScore() { return beforeRetentionScore; }
    public double getAfterRetentionScore() { return afterRetentionScore; }
    public int getBeforeAttempts() { return beforeAttempts; }
    public int getAfterAttempts() { return afterAttempts; }
    public int getBeforeCorrectAttempts() { return beforeCorrectAttempts; }
    public int getAfterCorrectAttempts() { return afterCorrectAttempts; }
    public String getEvidenceSource() { return evidenceSource; }
    public String getAssessmentType() { return assessmentType; }
    public String getDiagnosticCategory() { return diagnosticCategory; }
    public String getEvidenceText() { return evidenceText; }
    public Double getBehaviorTestPassRate() { return behaviorTestPassRate; }
    public int getDifficultyLevel() { return difficultyLevel; }
    public double getEvidenceWeight() { return evidenceWeight; }
    public boolean isHintUsed() { return hintUsed; }
    public boolean isIndependentEvidence() { return independentEvidence; }
    public String getStateModelVersion() { return stateModelVersion; }
    public String getRetentionModelVersion() { return retentionModelVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
