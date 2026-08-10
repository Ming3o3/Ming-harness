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
 * 学习任务的站内触达记录。
 *
 * <p>通知不是任务状态的替代品：任务仍由 {@link LearningTask} 负责业务结果，通知只记录
 * 某个需要行动的状态是否已经被用户确认。eventKey 让首次到期、延期后再次到期、失败重试
 * 等同一任务的多个业务事件可以分别追踪，并且在多实例调度下幂等。</p>
 */
@Entity
@Table(name = "harness_learning_task_notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_learning_task_notification_event",
        columnNames = {"tenant_id", "user_id", "learning_task_id", "event_key"}
))
public class LearningTaskNotification {

    @Id
    private String id;
    @Column(nullable = false, length = 255)
    private String tenantId;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 255)
    private String learningTaskId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningTaskNotificationType notificationType;
    @Column(nullable = false, length = 255)
    private String eventKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LearningTaskNotificationStatus status;
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

    protected LearningTaskNotification() {
    }

    public LearningTaskNotification(String tenantId, String userId, String learningTaskId,
                                    LearningTaskNotificationType notificationType, String eventKey,
                                    String title, String body, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = required(tenantId, "tenantId");
        this.userId = required(userId, "userId");
        this.learningTaskId = required(learningTaskId, "learningTaskId");
        this.notificationType = notificationType == null
                ? LearningTaskNotificationType.DUE : notificationType;
        this.eventKey = required(eventKey, "eventKey");
        this.status = LearningTaskNotificationStatus.UNREAD;
        this.title = required(title, "title");
        this.body = required(body, "body");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = this.createdAt;
    }

    public void markSeen(Instant observedAt) {
        if (seenAt == null) {
            seenAt = observedAt == null ? Instant.now() : observedAt;
        }
        updatedAt = Instant.now();
    }

    public void markRead(Instant readAt) {
        Instant now = readAt == null ? Instant.now() : readAt;
        if (seenAt == null) seenAt = now;
        status = LearningTaskNotificationStatus.READ;
        this.readAt = now;
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
    public String getLearningTaskId() { return learningTaskId; }
    public LearningTaskNotificationType getNotificationType() { return notificationType; }
    public String getEventKey() { return eventKey; }
    public LearningTaskNotificationStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSeenAt() { return seenAt; }
    public Instant getReadAt() { return readAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
