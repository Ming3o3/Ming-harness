package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.education.api.EducationMetricsView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationMetricsServiceTests {

    @Test
    void shouldReturnZeroRatesWhenParticipantHasNoEducationFacts() {
        EducationMetricsService service = service();

        EducationMetricsView metrics = service.summarize("tenant-a", "student-1");

        assertEquals(0, metrics.assignmentTotal());
        assertEquals(0.0, metrics.assignmentCompletionRate());
        assertEquals(0.0, metrics.taskEvidenceCoverageRate());
        assertEquals(0.0, metrics.notificationReadRate());
        assertEquals(0.0, metrics.assessmentAccuracyRate());
    }

    @Test
    void shouldAggregateParticipantFactsAndKeepTenantAndUserArguments() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningTaskNotificationRepository notifications = mock(LearningTaskNotificationRepository.class);
        LearningAssignmentNotificationRepository assignmentNotifications =
                mock(LearningAssignmentNotificationRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        when(assignments.countForParticipant("tenant-a", "student-1")).thenReturn(4L);
        when(assignments.countForParticipantByStatus("tenant-a", "student-1",
                LearningAssignmentStatus.ACCEPTED)).thenReturn(1L);
        when(assignments.countForParticipantByStatus("tenant-a", "student-1",
                LearningAssignmentStatus.AWAITING_EVIDENCE)).thenReturn(1L);
        when(assignments.countForParticipantByStatus("tenant-a", "student-1",
                LearningAssignmentStatus.COMPLETED)).thenReturn(2L);
        when(tasks.countByTenantIdAndUserId("tenant-a", "student-1")).thenReturn(5L);
        when(tasks.countByTenantIdAndUserIdAndStartedAtIsNotNull("tenant-a", "student-1"))
                .thenReturn(4L);
        when(tasks.countByTenantIdAndUserIdAndCompletedAtIsNotNull("tenant-a", "student-1"))
                .thenReturn(3L);
        when(tasks.countByTenantIdAndUserIdAndStatus("tenant-a", "student-1",
                LearningTaskStatus.AWAITING_EVIDENCE)).thenReturn(1L);
        when(tasks.countByTenantIdAndUserIdAndStatus("tenant-a", "student-1",
                LearningTaskStatus.FAILED)).thenReturn(1L);
        when(tasks.sumFailureCount("tenant-a", "student-1")).thenReturn(2L);
        when(tasks.countStartedWithAssessmentEvidence("tenant-a", "student-1"))
                .thenReturn(3L);
        when(notifications.countByTenantIdAndUserId("tenant-a", "student-1")).thenReturn(4L);
        when(notifications.countByTenantIdAndUserIdAndSeenAtIsNotNull("tenant-a", "student-1"))
                .thenReturn(3L);
        when(notifications.countByTenantIdAndUserIdAndReadAtIsNotNull("tenant-a", "student-1"))
                .thenReturn(2L);
        when(assignmentNotifications.countByTenantIdAndUserId("tenant-a", "student-1"))
                .thenReturn(2L);
        when(assignmentNotifications.countByTenantIdAndUserIdAndSeenAtIsNotNull("tenant-a", "student-1"))
                .thenReturn(1L);
        when(assignmentNotifications.countByTenantIdAndUserIdAndReadAtIsNotNull("tenant-a", "student-1"))
                .thenReturn(1L);
        when(assessments.countByTenantIdAndUserId("tenant-a", "student-1")).thenReturn(5L);
        when(assessments.countByTenantIdAndUserIdAndAssessmentType("tenant-a", "student-1",
                AssessmentAttemptType.FORMATIVE)).thenReturn(3L);
        when(assessments.countByTenantIdAndUserIdAndAssessmentType("tenant-a", "student-1",
                AssessmentAttemptType.REVIEW)).thenReturn(2L);
        when(assessments.countByTenantIdAndUserIdAndCorrectTrue("tenant-a", "student-1"))
                .thenReturn(4L);

        EducationMetricsView metrics = new EducationMetricsService(
                assignments, tasks, notifications, assignmentNotifications, assessments)
                .summarize("tenant-a", "student-1");

        assertEquals(4, metrics.assignmentTotal());
        assertEquals(4, metrics.assignmentAccepted());
        assertEquals(2, metrics.assignmentCompleted());
        assertEquals(1.0, metrics.assignmentAcceptanceRate());
        assertEquals(0.5, metrics.assignmentCompletionRate());
        assertEquals(0.8, metrics.taskStartRate());
        assertEquals(0.6, metrics.taskCompletionRate());
        assertEquals(0.75, metrics.taskEvidenceCoverageRate());
        assertEquals(6, metrics.notificationTotal());
        assertEquals(3, metrics.notificationRead());
        assertEquals(0.5, metrics.notificationReadRate());
        assertEquals(0.8, metrics.assessmentAccuracyRate());
    }

    @Test
    void shouldExposeTeacherReviewRetryRetentionAndMasteryOutcomeMetrics() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        LearningTaskNotificationRepository notifications = mock(LearningTaskNotificationRepository.class);
        LearningAssignmentNotificationRepository assignmentNotifications =
                mock(LearningAssignmentNotificationRepository.class);
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);

        when(assignments.countForParticipant("tenant-a", "student-1")).thenReturn(2L);
        when(assignments.countForParticipantByReviewStatus("tenant-a", "student-1",
                LearningAssignmentReviewStatus.PENDING)).thenReturn(1L);
        when(assignments.countForParticipantByReviewStatus("tenant-a", "student-1",
                LearningAssignmentReviewStatus.VERIFIED)).thenReturn(1L);
        when(assignments.countForParticipantByReviewStatus("tenant-a", "student-1",
                LearningAssignmentReviewStatus.REVISION_REQUIRED)).thenReturn(1L);
        when(tasks.countRetriedTasks("tenant-a", "student-1")).thenReturn(2L);
        when(tasks.countRetriedTasksCompleted("tenant-a", "student-1")).thenReturn(1L);
        LearningAssignmentFeedback feedback = new LearningAssignmentFeedback(
                "tenant-a", "assignment-1", "teacher-1", "student-1",
                LearningAssignmentFeedbackAction.COMMENT, "请确认依据", null,
                java.time.Instant.parse("2026-08-10T00:00:00Z"));
        feedback.acknowledge(java.time.Instant.parse("2026-08-10T00:01:30Z"));
        when(feedbacks.findByTenantIdAndParticipantOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(java.util.List.of(feedback));
        when(assessments.countByTenantIdAndUserIdAndAssessmentType("tenant-a", "student-1",
                AssessmentAttemptType.REVIEW)).thenReturn(2L);
        when(assessments.countByTenantIdAndUserIdAndAssessmentTypeAndCorrectTrue(
                "tenant-a", "student-1", AssessmentAttemptType.REVIEW)).thenReturn(1L);
        AssessmentAttempt attempt = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                "goal-1", "profile-1", "函数", true, 0.8, 0.2, 0.65,
                AssessmentAttemptType.FORMATIVE, null, "MODEL_TOOL", "依据", "通过");
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(java.util.List.of(attempt));

        EducationMetricsView metrics = new EducationMetricsService(
                assignments, tasks, notifications, assignmentNotifications, feedbacks, assessments)
                .summarize("tenant-a", "student-1");

        assertEquals(1, metrics.assignmentReviewPending());
        assertEquals(1, metrics.assignmentReviewVerified());
        assertEquals(1, metrics.assignmentReviewRevisionRequired());
        assertEquals(1.0 / 3.0, metrics.assignmentReviewVerificationRate());
        assertEquals(1, metrics.feedbackTotal());
        assertEquals(1, metrics.feedbackAcknowledged());
        assertEquals(1.0, metrics.feedbackAcknowledgementRate());
        assertEquals(0, metrics.feedbackResolved());
        assertEquals(0.0, metrics.feedbackResolutionRate());
        assertEquals(90, metrics.feedbackAcknowledgementLatencySeconds());
        assertEquals(2, metrics.retriedTaskTotal());
        assertEquals(1, metrics.retriedTaskCompleted());
        assertEquals(0.5, metrics.retrySuccessRate());
        assertEquals(0.45, metrics.averageMasteryGain());
        assertEquals(0.5, metrics.reviewAssessmentAccuracyRate());
    }

    private EducationMetricsService service() {
        return new EducationMetricsService(
                mock(LearningAssignmentRepository.class),
                mock(LearningTaskRepository.class),
                mock(LearningTaskNotificationRepository.class),
                mock(AssessmentAttemptRepository.class));
    }
}
