package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 学习者对单个知识点的可解释掌握度快照。 */
@Entity
@Table(name = "harness_learner_mastery", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learner_mastery_concept",
        columnNames = {"learner_profile_id", "concept_key"}
))
public class LearnerMastery {

    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String learnerProfileId;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Column(nullable = false)
    private double masteryScore;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private int correctAttempts;
    private Instant lastAssessedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearnerMastery() {
    }

    public LearnerMastery(String tenantId, String learnerProfileId, String conceptKey,
                          double masteryScore, int attempts, int correctAttempts) {
        this(tenantId, learnerProfileId, conceptKey, masteryScore, attempts, correctAttempts, Instant.now());
    }

    /** 支持导入历史测评时间，并让保持度计算在测试和重放中可复现。 */
    public LearnerMastery(String tenantId, String learnerProfileId, String conceptKey,
                          double masteryScore, int attempts, int correctAttempts,
                          Instant lastAssessedAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.masteryScore = clamp(masteryScore);
        this.attempts = Math.max(0, attempts);
        this.correctAttempts = Math.max(0, Math.min(this.attempts, correctAttempts));
        this.lastAssessedAt = lastAssessedAt == null ? Instant.now() : lastAssessedAt;
        this.updatedAt = this.lastAssessedAt;
    }

    public void recordAssessment(boolean correct, double observedMastery) {
        recordAssessment(correct, observedMastery, 3, 1.0, false);
    }

    /** 使用简化 BKT 更新单个知识点的掌握概率。 */
    public void recordAssessment(boolean correct, double observedMastery, int difficultyLevel,
                                 double evidenceWeight, boolean hintUsed) {
        attempts = Math.min(Integer.MAX_VALUE, attempts + 1);
        if (correct) correctAttempts = Math.min(attempts, correctAttempts + 1);
        // observedMastery 只作为旧数据兼容的软证据；新流程的主更新由 BKT 的 correct、
        // 难度、提示和证据权重共同决定，避免模型直接伪造最终掌握度。
        double bktScore = BktMasteryCalculator.update(masteryScore, correct, difficultyLevel,
                evidenceWeight, hintUsed).nextMastery();
        double observed = clamp(observedMastery);
        masteryScore = clamp(0.80 * bktScore + 0.20 * observed);
        lastAssessedAt = Instant.now();
        updatedAt = lastAssessedAt;
    }

    public void setMastery(double score, int attempts, int correctAttempts) {
        this.masteryScore = clamp(score);
        this.attempts = Math.max(0, attempts);
        this.correctAttempts = Math.max(0, Math.min(this.attempts, correctAttempts));
        this.lastAssessedAt = Instant.now();
        this.updatedAt = this.lastAssessedAt;
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
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getConceptKey() { return conceptKey; }
    public double getMasteryScore() { return masteryScore; }
    public int getAttempts() { return attempts; }
    public int getCorrectAttempts() { return correctAttempts; }
    public Instant getLastAssessedAt() { return lastAssessedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** 以指定时点计算保持度，不能在 Run 重放时隐式读取当前时间。 */
    public double retentionScoreAt(Instant asOf) {
        return LearnerRetentionModel.retentionAt(lastAssessedAt, asOf);
    }

    public double effectiveMasteryAt(Instant asOf) {
        return LearnerRetentionModel.effectiveMastery(masteryScore, retentionScoreAt(asOf));
    }

    public double forgettingRiskAt(Instant asOf) {
        return LearnerRetentionModel.forgettingRisk(retentionScoreAt(asOf));
    }

    public double confidenceLowerAt(Instant asOf) {
        return new LearnerStateEvidence(masteryScore, attempts, correctAttempts,
                lastAssessedAt, retentionScoreAt(asOf)).confidenceLower();
    }

    public double confidenceUpperAt(Instant asOf) {
        return new LearnerStateEvidence(masteryScore, attempts, correctAttempts,
                lastAssessedAt, retentionScoreAt(asOf)).confidenceUpper();
    }

    /** 近似 95% 可信范围；证据越少区间越宽，供页面解释而不是替代 BKT 状态。 */
    public double getConfidenceLower() { return confidenceInterval()[0]; }
    public double getConfidenceUpper() { return confidenceInterval()[1]; }

    private double[] confidenceInterval() {
        if (attempts <= 0) return new double[]{0.0, 1.0};
        double n = Math.max(1.0, attempts);
        double standardError = Math.sqrt(Math.max(0.0, masteryScore * (1.0 - masteryScore) / n));
        return new double[]{clamp(masteryScore - 1.96 * standardError),
                clamp(masteryScore + 1.96 * standardError)};
    }
}
