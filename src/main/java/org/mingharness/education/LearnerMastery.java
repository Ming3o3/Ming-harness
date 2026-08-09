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
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.masteryScore = clamp(masteryScore);
        this.attempts = Math.max(0, attempts);
        this.correctAttempts = Math.max(0, Math.min(this.attempts, correctAttempts));
        this.lastAssessedAt = Instant.now();
        this.updatedAt = this.lastAssessedAt;
    }

    public void recordAssessment(boolean correct, double observedMastery) {
        attempts = Math.min(Integer.MAX_VALUE, attempts + 1);
        if (correct) correctAttempts = Math.min(attempts, correctAttempts + 1);
        // 指数平滑保留历史表现，同时让近期小测能逐步改变画像。
        masteryScore = clamp(0.7 * masteryScore + 0.3 * observedMastery);
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
}
