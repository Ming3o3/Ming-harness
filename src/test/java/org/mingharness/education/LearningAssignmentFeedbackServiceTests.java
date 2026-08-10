package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.education.api.LearningAssignmentFeedbackRequest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAssignmentFeedbackServiceTests {

    @Test
    void shouldPersistTeacherFeedbackAndNotifyLearner() {
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentRepository assignmentRepository = mock(LearningAssignmentRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);
        when(feedbacks.save(any(LearningAssignmentFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentFeedbackService service = new LearningAssignmentFeedbackService(
                feedbacks, assignments, assignmentRepository, notifications, new org.mingharness.common.SensitiveDataSanitizer());
        var view = service.create("tenant-a", "teacher-1", assignment.getId(),
                new LearningAssignmentFeedbackRequest("REQUEST_EVIDENCE", "请补充函数定义域的作答过程。", null));

        assertEquals("REQUEST_EVIDENCE", view.action());
        assertEquals("OPEN", view.status());
        verify(notifications).ensureForFeedback(any(LearningAssignmentFeedback.class), org.mockito.ArgumentMatchers.eq(assignment));
    }

    @Test
    void shouldRescheduleOverdueAssignmentAsPartOfTeacherIntervention() {
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignmentRepository assignmentRepository = mock(LearningAssignmentRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        assignment.accept("profile-1", "goal-1", Instant.now().minusSeconds(100));
        assignment.markOverdue(Instant.now());
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);
        when(feedbacks.save(any(LearningAssignmentFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentFeedbackService service = new LearningAssignmentFeedbackService(
                feedbacks, assignments, assignmentRepository, notifications, new org.mingharness.common.SensitiveDataSanitizer());
        Instant nextDueAt = Instant.now().plusSeconds(3600);
        service.create("tenant-a", "teacher-1", assignment.getId(),
                new LearningAssignmentFeedbackRequest("RESCHEDULE", "延长一天完成。", nextDueAt));

        assertEquals(LearningAssignmentStatus.ACCEPTED, assignment.getStatus());
        assertEquals(nextDueAt, assignment.getDueAt());
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void shouldRejectFeedbackFromLearner() {
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignment assignmentRepositoryValue = assignment();
        when(assignments.getForParticipant("tenant-a", "student-1", assignmentRepositoryValue.getId()))
                .thenReturn(assignmentRepositoryValue);
        LearningAssignmentFeedbackService service = new LearningAssignmentFeedbackService(
                feedbacks, assignments, mock(LearningAssignmentRepository.class),
                mock(LearningAssignmentNotificationService.class), new org.mingharness.common.SensitiveDataSanitizer());

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.create(
                "tenant-a", "student-1", assignmentRepositoryValue.getId(),
                new LearningAssignmentFeedbackRequest("COMMENT", "尝试一下。", null)));

        assertEquals("LEARNING_ASSIGNMENT_TEACHER_ONLY", exception.getCode());
    }

    @Test
    void shouldAcknowledgeFeedbackOnlyForItsLearner() {
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningAssignment assignment = assignment();
        LearningAssignmentFeedback feedback = new LearningAssignmentFeedback(
                "tenant-a", assignment.getId(), "teacher-1", "student-1",
                LearningAssignmentFeedbackAction.COMMENT, "请继续完成。", null, Instant.now());
        when(assignments.getForParticipant("tenant-a", "student-1", assignment.getId()))
                .thenReturn(assignment);
        when(feedbacks.findByTenantIdAndLearningAssignmentIdAndId(
                "tenant-a", assignment.getId(), feedback.getId())).thenReturn(Optional.of(feedback));
        when(feedbacks.save(feedback)).thenReturn(feedback);

        LearningAssignmentFeedbackService service = new LearningAssignmentFeedbackService(
                feedbacks, assignments, mock(LearningAssignmentRepository.class),
                mock(LearningAssignmentNotificationService.class), new org.mingharness.common.SensitiveDataSanitizer());

        assertEquals("ACKNOWLEDGED", service.acknowledge(
                "tenant-a", "student-1", assignment.getId(), feedback.getId()).status());
        assertEquals(LearningAssignmentFeedbackStatus.ACKNOWLEDGED, feedback.getStatus());
    }

    private LearningAssignment assignment() {
        return new LearningAssignment("tenant-a", "teacher-1", "student-1", "函数作业",
                "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600));
    }
}
