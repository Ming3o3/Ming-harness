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

/** 课程作业业务状态的站内触达记录，与学习任务通知分表隔离。 */
@Entity
@Table(name = "harness_learning_assignment_notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learning_assignment_notification_event",
        columnNames = {"tenant_id", "user_id", "learning_assignment_id", "event_key"}
))
public class LearningAssignmentNotification {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 255)
    private String learningAssignmentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAssignmentNotificationType notificationType;
    @Column(nullable = false, length = 255)
    private String eventKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LearningAssignmentNotificationStatus status;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant seenAt;
    private Instant readAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LearningAssignmentNotification() {
    }

    public LearningAssignmentNotification(String tenantId, String userId, String learningAssignmentId,
                                          LearningAssignmentNotificationType notificationType,
                                          String eventKey, String title, String body, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.learningAssignmentId = required(learningAssignmentId, "learningAssignmentId");
        this.notificationType = notificationType == null
                ? LearningAssignmentNotificationType.ASSIGNED : notificationType;
        this.eventKey = required(eventKey, "eventKey");
        this.status = LearningAssignmentNotificationStatus.UNREAD;
        this.title = required(title, "title");
        this.body = required(body, "body");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = this.createdAt;
    }

    public void markSeen(Instant observedAt) {
        if (seenAt == null) seenAt = observedAt == null ? Instant.now() : observedAt;
        updatedAt = Instant.now();
    }

    public void markRead(Instant observedAt) {
        Instant at = observedAt == null ? Instant.now() : observedAt;
        if (seenAt == null) seenAt = at;
        status = LearningAssignmentNotificationStatus.READ;
        readAt = at;
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
    public String getLearningAssignmentId() { return learningAssignmentId; }
    public LearningAssignmentNotificationType getNotificationType() { return notificationType; }
    public String getEventKey() { return eventKey; }
    public LearningAssignmentNotificationStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSeenAt() { return seenAt; }
    public Instant getReadAt() { return readAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
