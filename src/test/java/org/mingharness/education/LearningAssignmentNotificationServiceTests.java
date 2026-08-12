package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.education.api.LearningAssignmentNotificationView;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAssignmentNotificationServiceTests {

    @Test
    void shouldCreateAnIdempotentAssignedNotificationForLearner() {
        LearningAssignmentNotificationRepository notifications = mock(LearningAssignmentNotificationRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = assignment();
        when(notifications.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                "tenant-a", "student-1", assignment.getId(), "ASSIGNED"))
                .thenReturn(Optional.empty());
        when(notifications.save(any(LearningAssignmentNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentNotificationService service = new LearningAssignmentNotificationService(notifications, assignments);
        service.ensureForState(assignment);

        var captor = org.mockito.ArgumentCaptor.forClass(LearningAssignmentNotification.class);
        verify(notifications).save(captor.capture());
        assertEquals("student-1", captor.getValue().getUserId());
        assertEquals(LearningAssignmentNotificationType.ASSIGNED, captor.getValue().getNotificationType());
        assertEquals("ASSIGNED", captor.getValue().getEventKey());
    }

    @Test
    void shouldNotifyBothParticipantsWhenAssignmentExpires() {
        LearningAssignmentNotificationRepository notifications = mock(LearningAssignmentNotificationRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = assignment();
        assignment.markOverdue(Instant.now());
        when(notifications.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                eq("tenant-a"), any(String.class), eq(assignment.getId()), eq("OVERDUE")))
                .thenReturn(Optional.empty());
        when(notifications.save(any(LearningAssignmentNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentNotificationService service = new LearningAssignmentNotificationService(notifications, assignments);
        service.ensureForState(assignment);

        verify(notifications, org.mockito.Mockito.times(2)).save(any(LearningAssignmentNotification.class));
        assertTrue(assignment.getStatus() == LearningAssignmentStatus.OVERDUE);
    }

    @Test
    void shouldResolveOldStateNotificationWhenAssignmentMovesForward() {
        LearningAssignmentNotificationRepository notifications = mock(LearningAssignmentNotificationRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = assignment();
        LearningAssignmentNotification old = new LearningAssignmentNotification(
                "tenant-a", "student-1", assignment.getId(), LearningAssignmentNotificationType.ASSIGNED,
                "ASSIGNED", "收到课程作业", "请接受", Instant.now());
        assignment.accept("profile-1", "goal-1", Instant.now());
        when(notifications.findByTenantIdAndUserIdAndLearningAssignmentIdAndStatus(
                eq("tenant-a"), any(String.class), eq(assignment.getId()),
                eq(LearningAssignmentNotificationStatus.UNREAD)))
                .thenReturn(java.util.List.of(old), java.util.List.of());
        when(notifications.findByTenantIdAndUserIdAndLearningAssignmentIdAndEventKey(
                eq("tenant-a"), any(String.class), eq(assignment.getId()), any(String.class)))
                .thenReturn(Optional.empty());
        when(notifications.save(any(LearningAssignmentNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentNotificationService service = new LearningAssignmentNotificationService(notifications, assignments);
        service.ensureForState(assignment);

        assertEquals(LearningAssignmentNotificationStatus.READ, old.getStatus());
        verify(notifications).saveAll(java.util.List.of(old));
        verify(notifications).save(any(LearningAssignmentNotification.class));
    }

    @Test
    void shouldMarkReadAndReturnAssignmentContext() {
        LearningAssignmentNotificationRepository notifications = mock(LearningAssignmentNotificationRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignment assignment = assignment();
        LearningAssignmentNotification notification = new LearningAssignmentNotification(
                "tenant-a", "student-1", assignment.getId(), LearningAssignmentNotificationType.ASSIGNED,
                "ASSIGNED", "收到课程作业", "请接受", Instant.now());
        when(notifications.findByTenantIdAndUserIdAndId("tenant-a", "student-1", notification.getId()))
                .thenReturn(Optional.of(notification));
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(notifications.save(notification)).thenReturn(notification);

        LearningAssignmentNotificationService service = new LearningAssignmentNotificationService(notifications, assignments);
        LearningAssignmentNotificationView view = service.markRead("tenant-a", "student-1", notification.getId());

        assertEquals("READ", view.status());
        assertEquals(assignment.getId(), view.learningAssignmentId());
        assertTrue(!view.unread());
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600));
    }
}
