package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentReviewRequest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
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
                rubric("VERIFY", "已核对作答依据"));

        assertEquals("VERIFIED", result.reviewStatus());
        assertEquals("teacher-1", result.teacherReviewerUserId());
        assertEquals("已核对作答依据", result.teacherReviewNote());
        verify(notifications).resolveForAssignmentState(
                "tenant-a", assignment.getId(), LearningAssignmentNotificationType.REVIEW_REQUIRED);
        verify(notifications).ensureForTeacherReviewVerified(assignment);
    }

    @Test
    void shouldPersistImmutableTeacherRubricEvaluation() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignmentEvaluationRepository evaluations = mock(LearningAssignmentEvaluationRepository.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(LearningAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evaluations.save(any(LearningAssignmentEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, null, null, notifications, new SensitiveDataSanitizer(), null, evaluations);
        service.review("tenant-a", "teacher-1", assignment.getId(),
                rubric("VERIFY", "已核对作答依据"));

        var captured = forClass(LearningAssignmentEvaluation.class);
        verify(evaluations).save(captured.capture());
        assertEquals(LearningAssignmentEvaluationDecision.VERIFY, captured.getValue().getDecision());
        assertEquals(5, captured.getValue().getContentCorrectnessScore());
        assertEquals(4, captured.getValue().getEvidenceQualityScore());
        assertEquals(3, captured.getValue().getTransferReadinessScore());
        assertEquals("education-v1", captured.getValue().getRubricVersion());
    }

    @Test
    void shouldRejectReviewWithoutTeacherRubric() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));

        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, notifications, new SensitiveDataSanitizer());
        var exception = assertThrows(org.mingharness.common.BusinessException.class, () ->
                service.review("tenant-a", "teacher-1", assignment.getId(),
                        new LearningAssignmentReviewRequest("VERIFY", "已核对作答依据")));

        assertEquals("LEARNING_ASSIGNMENT_RUBRIC_REQUIRED", exception.getCode());
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

    @Test
    void shouldReturnCompletedAssignmentForRevisionAndReactivateGoal() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "函数作业", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(goals.findByIdAndTenantIdAndUserId(assignment.getLearningGoalId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(assignments.save(any(LearningAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(goals.save(any(LearningGoal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, goals, notifications, new SensitiveDataSanitizer());
        var result = service.review("tenant-a", "teacher-1", assignment.getId(),
                rubric("RETURN", "请补充定义域判定依据"));

        assertEquals("RETRY_REQUIRED", result.status());
        assertEquals("REVISION_REQUIRED", result.reviewStatus());
        assertEquals(LearningGoalStatus.ACTIVE, goal.getStatus());
        assertEquals(true, goal.isRevisionPending());
        assertEquals(1, goal.getRevisionCount());
        verify(notifications).ensureForTeacherRevisionRequired(assignment);
    }

    @Test
    void shouldRequireNoteWhenReturningAssignment() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, goals, mock(LearningAssignmentNotificationService.class),
                new SensitiveDataSanitizer());

        var exception = assertThrows(org.mingharness.common.BusinessException.class,
                () -> service.review("tenant-a", "teacher-1", assignment.getId(),
                        new LearningAssignmentReviewRequest("RETURN", " ")));
        assertEquals("LEARNING_ASSIGNMENT_REVISION_NOTE_REQUIRED", exception.getCode());
    }

    @Test
    void shouldRequireLearnerSubmissionBeforeTeacherVerification() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentSubmissionRepository submissions = mock(LearningAssignmentSubmissionRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        LearningAssignment assignment = assignment();
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(submissions.existsByTenantIdAndLearningAssignmentId("tenant-a", assignment.getId()))
                .thenReturn(false);

        LearningAssignmentReviewService service = new LearningAssignmentReviewService(
                assignments, null, null, notifications, new SensitiveDataSanitizer(), submissions);
        var exception = assertThrows(org.mingharness.common.BusinessException.class,
                () -> service.review("tenant-a", "teacher-1", assignment.getId(),
                        rubric("VERIFY", "已核对作答依据")));

        assertEquals("ASSIGNMENT_SUBMISSION_REQUIRED_FOR_REVIEW", exception.getCode());
    }

    private LearningAssignment assignment() {
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                Instant.now().plusSeconds(3600));
        assignment.accept("profile-1", "goal-1", Instant.now());
        assignment.complete(Instant.now());
        return assignment;
    }

    private LearningAssignmentReviewRequest rubric(String decision, String note) {
        return new LearningAssignmentReviewRequest(decision, note, 5, 4, 3);
    }
}
