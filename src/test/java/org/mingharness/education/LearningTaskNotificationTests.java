package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LearningTaskNotificationTests {

    @Test
    void shouldRecordSeenAndReadSeparately() {
        LearningTaskNotification notification = new LearningTaskNotification(
                "tenant-a", "student-1", "task-1", LearningTaskNotificationType.DUE,
                "OPEN:D0:F0", "复习任务待处理", "请开始复习", Instant.now());

        Instant seenAt = Instant.now();
        notification.markSeen(seenAt);
        assertEquals(LearningTaskNotificationStatus.UNREAD, notification.getStatus());
        assertEquals(seenAt, notification.getSeenAt());
        assertNotNull(notification.getUpdatedAt());

        Instant readAt = seenAt.plusSeconds(2);
        notification.markRead(readAt);
        assertEquals(LearningTaskNotificationStatus.READ, notification.getStatus());
        assertEquals(readAt, notification.getReadAt());
        assertEquals(seenAt, notification.getSeenAt());
    }
}
