package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentReviewRequest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAssignmentReviewServiceTests {

    @Test
    void shouldLetOnlyTheTeacherVerifyACompletedAssignment() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(LearningAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, notifications, new SensitiveDataSanitizer());
        var result = service.review("tenant-a", "teacher-1", assignment.getId(),
                new LearningAssignmentReviewRequest("VERIFY", "已核对作答依据"));

        assertEquals("VERIFIED", result.reviewStatus());
        assertEquals("teacher-1", result.teacherReviewerUserId());
        assertEquals("已核对作答依据", result.teacherReviewNote());
        verify(notifications).resolveForAssignmentState(
                "tenant-a", assignment.getId(), LearningAssignmentNotificationType.REVIEW_REQUIRED);
        verify(notifications).ensureForTeacherReviewVerified(assignment);
    }

    @Test
    void shouldRejectNonTeacherAndNonPendingReview() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, notifications, new SensitiveDataSanitizer());

        var forbidden = assertThrows(org.mingharness.common.BusinessException.class,
                () -> service.review("tenant-a", "student-1", assignment.getId(),
                        new LearningAssignmentReviewRequest("VERIFY", null)));
        assertEquals("LEARNING_ASSIGNMENT_TEACHER_ONLY", forbidden.getCode());

        LearningAssignment notPendingAssignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业2", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600));
        notPendingAssignment.accept("profile-1", "goal-2", Instant.now());
        when(assignments.findByTenantIdAndId("tenant-a", notPendingAssignment.getId()))
                .thenReturn(Optional.of(notPendingAssignment));
        var notPending = assertThrows(org.mingharness.common.BusinessException.class,
                () -> service.review("tenant-a", "teacher-1", notPendingAssignment.getId(),
                        new LearningAssignmentReviewRequest("VERIFY", null)));
        assertEquals("LEARNING_ASSIGNMENT_REVIEW_NOT_PENDING", notPending.getCode());
    }

    private LearningAssignment assignment() {
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600));
        assignment.accept("profile-1", "goal-1", Instant.now());
        assignment.complete(Instant.now());
        return assignment;
    }
}
