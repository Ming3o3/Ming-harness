package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 完成目标后的保持度复习计划；每次复习会推进下一次到期时间。 */
@Entity
@Table(name = "harness_learning_review_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learning_review_goal",
        columnNames = {"tenant_id", "user_id", "learning_goal_id"}
))
public class LearningReviewPlan {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 255)
    private String learningGoalId;
    @Column(nullable = false, length = 255)
    private String learnerProfileId;
    @Column(nullable = false, length = 255)
    private String conceptKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningReviewPlanStatus status;
    @Column(nullable = false)
    private int reviewCount;
    @Column(nullable = false)
    private int successfulReviewCount;
    @Column(nullable = false)
    private int intervalDays;
    @Column(nullable = false)
    private Instant nextReviewAt;
    private Instant lastReviewedAt;
    private Boolean lastReviewCorrect;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearningReviewPlan() {
    }

    public LearningReviewPlan(String tenantId, String userId, String learningGoalId,
                              String learnerProfileId, String conceptKey, Instant firstReviewAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.learningGoalId = required(learningGoalId, "learningGoalId");
        this.learnerProfileId = required(learnerProfileId, "learnerProfileId");
        this.conceptKey = required(conceptKey, "conceptKey");
        this.status = LearningReviewPlanStatus.ACTIVE;
        this.reviewCount = 0;
        this.successfulReviewCount = 0;
        this.intervalDays = 0;
        this.nextReviewAt = firstReviewAt == null ? Instant.now() : firstReviewAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isDue(Instant now) {
        Instant reference = now == null ? Instant.now() : now;
        return status == LearningReviewPlanStatus.ACTIVE && !nextReviewAt.isAfter(reference);
    }

    /** 采用可解释的间隔扩展：成功后 1、2、4... 天，失败后回到 1 天。 */
    public void recordReview(boolean correct, Instant reviewedAt) {
        Instant reviewed = reviewedAt == null ? Instant.now() : reviewedAt;
        reviewCount = Math.min(Integer.MAX_VALUE, reviewCount + 1);
        if (correct) {
            successfulReviewCount = Math.min(reviewCount, successfulReviewCount + 1);
            intervalDays = intervalDays <= 0 ? 1 : Math.min(30, Math.max(1, intervalDays * 2));
        } else {
            intervalDays = 1;
        }
        lastReviewedAt = reviewed;
        lastReviewCorrect = correct;
        nextReviewAt = reviewed.plus(intervalDays, ChronoUnit.DAYS);
        updatedAt = Instant.now();
    }

    public void changeStatus(LearningReviewPlanStatus nextStatus) {
        if (nextStatus == null) throw new IllegalArgumentException("复习计划状态不能为空");
        if (status == LearningReviewPlanStatus.ARCHIVED && nextStatus != LearningReviewPlanStatus.ARCHIVED) {
            throw new IllegalStateException("已归档的复习计划不能恢复");
        }
        status = nextStatus;
        updatedAt = Instant.now();
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getLearningGoalId() { return learningGoalId; }
    public String getLearnerProfileId() { return learnerProfileId; }
    public String getConceptKey() { return conceptKey; }
    public LearningReviewPlanStatus getStatus() { return status; }
    public int getReviewCount() { return reviewCount; }
    public int getSuccessfulReviewCount() { return successfulReviewCount; }
    public int getIntervalDays() { return intervalDays; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public Boolean getLastReviewCorrect() { return lastReviewCorrect; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
