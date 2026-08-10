package org.mingharness.education;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 教师对作业证据的反馈和后续动作，作为学习者下一步行动的业务凭据。 */
@Entity
@Table(name = "harness_learning_assignment_feedback")
public class LearningAssignmentFeedback {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String learningAssignmentId;
    @Column(nullable = false, length = 255)
    private String teacherUserId;
    @Column(nullable = false, length = 255)
    private String learnerUserId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAssignmentFeedbackAction action;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAssignmentFeedbackStatus status;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    private Instant suggestedDueAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant acknowledgedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearningAssignmentFeedback() {
    }

    public LearningAssignmentFeedback(String tenantId, String learningAssignmentId,
                                      String teacherUserId, String learnerUserId,
                                      LearningAssignmentFeedbackAction action, String message,
                                      Instant suggestedDueAt, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.learningAssignmentId = required(learningAssignmentId, "learningAssignmentId");
        this.teacherUserId = required(teacherUserId, "teacherUserId");
        this.learnerUserId = required(learnerUserId, "learnerUserId");
        this.action = action == null ? LearningAssignmentFeedbackAction.COMMENT : action;
        this.message = required(message, "message");
        this.suggestedDueAt = suggestedDueAt;
        this.status = LearningAssignmentFeedbackStatus.OPEN;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = this.createdAt;
    }

    public void acknowledge(Instant acknowledgedAt) {
        if (status == LearningAssignmentFeedbackStatus.ACKNOWLEDGED) return;
        Instant at = acknowledgedAt == null ? Instant.now() : acknowledgedAt;
        this.status = LearningAssignmentFeedbackStatus.ACKNOWLEDGED;
        this.acknowledgedAt = at;
        this.updatedAt = Instant.now();
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return normalized;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public String getTeacherUserId() { return teacherUserId; }
    public String getLearnerUserId() { return learnerUserId; }
    public LearningAssignmentFeedbackAction getAction() { return action; }
    public LearningAssignmentFeedbackStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getSuggestedDueAt() { return suggestedDueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
