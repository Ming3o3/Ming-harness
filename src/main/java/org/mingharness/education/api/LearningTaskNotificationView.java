package org.mingharness.education.api;

import org.mingharness.education.LearningTask;
import org.mingharness.education.LearningTaskNotification;

import java.time.Instant;

/** 站内学习任务通知的安全投影。 */
public record LearningTaskNotificationView(
        String id,
        String learningTaskId,
        String taskStatus,
        String notificationType,
        String title,
        String body,
        String status,
        boolean unread,
        Instant scheduledAt,
        Instant createdAt,
        Instant seenAt,
        Instant readAt,
        Instant updatedAt
) {

    public static LearningTaskNotificationView from(LearningTaskNotification notification,
                                                     LearningTask task) {
        return new LearningTaskNotificationView(
                notification.getId(), notification.getLearningTaskId(),
                task == null ? null : task.getStatus().name(),
                notification.getNotificationType().name(), notification.getTitle(), notification.getBody(),
                notification.getStatus().name(),
                notification.getStatus() == org.mingharness.education.LearningTaskNotificationStatus.UNREAD,
                task == null ? null : task.getScheduledAt(), notification.getCreatedAt(),
                notification.getSeenAt(), notification.getReadAt(), notification.getUpdatedAt());
    }
}
