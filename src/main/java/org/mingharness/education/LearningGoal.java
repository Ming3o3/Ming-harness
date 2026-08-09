package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 绑定学习者画像的可追踪学习目标。 */
@Entity
@Table(name = "harness_learning_goals")
public class LearningGoal {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 255)
    private String learnerProfileId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Column(nullable = false)
    private double baselineMastery;
    @Column(nullable = false)
    private double targetMastery;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningGoalStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant completedAt;

    protected LearningGoal() {
    }

    public LearningGoal(String tenantId, String userId, String learnerProfileId,
                        String title, String conceptKey, double baselineMastery,
                        double targetMastery) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.title = required(title, "title");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.baselineMastery = clamp(baselineMastery);
        this.targetMastery = clamp(targetMastery);
        if (this.targetMastery <= this.baselineMastery) {
            throw new IllegalArgumentException("targetMastery 必须高于当前掌握度");
        }
        this.status = LearningGoalStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(LearningGoalStatus nextStatus) {
        if (nextStatus == null) throw new IllegalArgumentException("目标状态不能为空");
        if (status == LearningGoalStatus.ARCHIVED && nextStatus != LearningGoalStatus.ARCHIVED) {
            throw new IllegalStateException("已归档的学习目标不能恢复");
        }
        if (status == LearningGoalStatus.COMPLETED
                && nextStatus != LearningGoalStatus.COMPLETED
                && nextStatus != LearningGoalStatus.ARCHIVED) {
            throw new IllegalStateException("已完成的学习目标不能恢复为进行中或暂停");
        }
        status = nextStatus;
        completedAt = nextStatus == LearningGoalStatus.COMPLETED ? Instant.now() : null;
        updatedAt = Instant.now();
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
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getTitle() { return title; }
    public String getConceptKey() { return conceptKey; }
    public double getBaselineMastery() { return baselineMastery; }
    public double getTargetMastery() { return targetMastery; }
    public LearningGoalStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
