package org.mingharness.education.api;

import org.mingharness.education.LearningAssignment;
import org.mingharness.education.LearningAssignmentNotification;

import java.time.Instant;

/** 课程作业通知的安全投影。 */
public record LearningAssignmentNotificationView(
        String id,
        String learningAssignmentId,
        String assignmentStatus,
        String notificationType,
        String title,
        String body,
        String status,
        boolean unread,
        Instant dueAt,
        Instant createdAt,
        Instant seenAt,
        Instant readAt,
        Instant updatedAt
) {
    public static LearningAssignmentNotificationView from(
            LearningAssignmentNotification notification, LearningAssignment assignment) {
        return new LearningAssignmentNotificationView(
                notification.getId(), notification.getLearningAssignmentId(),
                assignment == null ? null : assignment.getStatus().name(),
                notification.getNotificationType().name(), notification.getTitle(), notification.getBody(),
                notification.getStatus().name(),
                notification.getStatus() == org.mingharness.education.LearningAssignmentNotificationStatus.UNREAD,
                assignment == null ? null : assignment.getDueAt(), notification.getCreatedAt(),
                notification.getSeenAt(), notification.getReadAt(), notification.getUpdatedAt());
    }
}
