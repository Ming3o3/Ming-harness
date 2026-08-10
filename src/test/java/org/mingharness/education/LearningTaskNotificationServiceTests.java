package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningTaskNotificationServiceTests {

    @Test
    void shouldMaterializeAnOpenNotificationIdempotently() {
        LearningTaskNotificationRepository notifications = mock(LearningTaskNotificationRepository.class);
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningTask task = task();
        LearningTaskNotification saved = new LearningTaskNotification(
                "tenant-a", "student-1", task.getId(), LearningTaskNotificationType.DUE,
                "OPEN:D0:F0", "复习任务待处理", "请开始复习", Instant.now());
        when(notifications.findByTenantIdAndUserIdAndLearningTaskIdAndEventKey(
                "tenant-a", "student-1", task.getId(), "OPEN:D0:F0"))
                .thenReturn(Optional.empty(), Optional.of(saved));
        when(notifications.save(any(LearningTaskNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningTaskNotificationService service = new LearningTaskNotificationService(notifications, tasks);
        service.ensureForTaskState(task);
        service.ensureForTaskState(task);

        ArgumentCaptor<LearningTaskNotification> captor =
                ArgumentCaptor.forClass(LearningTaskNotification.class);
        verify(notifications, times(1)).save(captor.capture());
        assertEquals(LearningTaskNotificationType.DUE, captor.getValue().getNotificationType());
        assertEquals("OPEN:D0:F0", captor.getValue().getEventKey());
    }

    @Test
    void shouldUseANewEventKeyAfterDeferral() {
        LearningTaskNotificationRepository notifications = mock(LearningTaskNotificationRepository.class);
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningTask task = task();
        when(notifications.findByTenantIdAndUserIdAndLearningTaskIdAndEventKey(
                "tenant-a", "student-1", task.getId(), "OPEN:D0:F0"))
                .thenReturn(Optional.empty());
        when(notifications.findByTenantIdAndUserIdAndLearningTaskIdAndEventKey(
                "tenant-a", "student-1", task.getId(), "OPEN:D1:F0"))
                .thenReturn(Optional.empty());
        when(notifications.save(any(LearningTaskNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningTaskNotificationService service = new LearningTaskNotificationService(notifications, tasks);
        service.ensureForTaskState(task);
        task.deferUntil(Instant.now().plusSeconds(60), Instant.now());
        task.makeAvailable(Instant.now().plusSeconds(60));
        service.ensureForTaskState(task);

        verify(notifications, times(2)).save(any(LearningTaskNotification.class));
    }

    private LearningTask task() {
        return new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                "goal-1", "plan-1", 0, "保持度复习", "请完成复习题", Instant.now());
    }
}
