package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 面向学习者的可执行业务任务。
 *
 * <p>复习计划描述长期节奏，任务描述某一次需要被触达和完成的业务实例。
 * 两者分离后，任务可以拥有会话、Run、延期和结果，而不会把计划本身误当成一次执行。</p>
 */
@Entity
@Table(name = "harness_learning_tasks", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learning_task_review_occurrence",
        columnNames = {"tenant_id", "user_id", "review_plan_id", "review_sequence"}
))
public class LearningTask {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningTaskType taskType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningTaskStatus status;
    @Column(nullable = false, length = 255)
    private String learningGoalId;
    @Column(nullable = false, length = 255)
    private String reviewPlanId;
    @Column(nullable = false)
    private int reviewSequence;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String prompt;
    @Column(nullable = false)
    private Instant scheduledAt;
    private String conversationId;
    private String runId;
    private Instant startedAt;
    private Instant completedAt;
    private Boolean outcomeCorrect;
    @Column(nullable = false)
    private int deferCount;
    private Instant lastDeferredAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearningTask() {
    }

    public LearningTask(String tenantId, String userId, LearningTaskType taskType,
                        String learningGoalId, String reviewPlanId, int reviewSequence,
                        String title, String prompt, Instant scheduledAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.taskType = taskType == null ? LearningTaskType.REVIEW : taskType;
        this.status = LearningTaskStatus.OPEN;
        this.learningGoalId = required(learningGoalId, "learningGoalId");
        this.reviewPlanId = required(reviewPlanId, "reviewPlanId");
        if (reviewSequence < 0) throw new IllegalArgumentException("reviewSequence 不能小于 0");
        this.reviewSequence = reviewSequence;
        this.title = required(title, "title");
        this.prompt = required(prompt, "prompt");
        this.scheduledAt = scheduledAt == null ? Instant.now() : scheduledAt;
        this.deferCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void start(String conversationId, String runId, Instant startedAt) {
        if (status != LearningTaskStatus.OPEN) {
            throw new IllegalStateException("只有待执行的学习任务可以开始");
        }
        this.conversationId = required(conversationId, "conversationId");
        this.runId = required(runId, "runId");
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
        this.status = LearningTaskStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    /** 到期后由调度器把延期任务重新变为可执行。 */
    public void makeAvailable(Instant now) {
        if (status != LearningTaskStatus.DEFERRED) return;
        this.status = LearningTaskStatus.OPEN;
        this.updatedAt = now == null ? Instant.now() : now;
    }

    public void deferUntil(Instant nextScheduledAt, Instant deferredAt) {
        if (status != LearningTaskStatus.OPEN) {
            throw new IllegalStateException("只有待执行的学习任务可以延期");
        }
        Instant next = nextScheduledAt == null ? Instant.now() : nextScheduledAt;
        this.scheduledAt = next;
        this.deferCount = Math.min(Integer.MAX_VALUE, deferCount + 1);
        this.lastDeferredAt = deferredAt == null ? Instant.now() : deferredAt;
        this.status = LearningTaskStatus.DEFERRED;
        this.updatedAt = Instant.now();
    }

    public void complete(boolean correct, Instant completedAt) {
        if (status != LearningTaskStatus.OPEN && status != LearningTaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("当前学习任务不能记录完成结果");
        }
        this.outcomeCorrect = correct;
        this.completedAt = completedAt == null ? Instant.now() : completedAt;
        this.status = LearningTaskStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == LearningTaskStatus.COMPLETED) {
            throw new IllegalStateException("已完成的学习任务不能取消");
        }
        this.status = LearningTaskStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public LearningTaskType getTaskType() { return taskType; }
    public LearningTaskStatus getStatus() { return status; }
    public String getLearningGoalId() { return learningGoalId; }
    public String getReviewPlanId() { return reviewPlanId; }
    public int getReviewSequence() { return reviewSequence; }
    public String getTitle() { return title; }
    public String getPrompt() { return prompt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getConversationId() { return conversationId; }
    public String getRunId() { return runId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Boolean getOutcomeCorrect() { return outcomeCorrect; }
    public int getDeferCount() { return deferCount; }
    public Instant getLastDeferredAt() { return lastDeferredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
