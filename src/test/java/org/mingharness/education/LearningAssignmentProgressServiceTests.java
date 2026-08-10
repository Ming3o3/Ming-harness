package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.education.api.LearningAssignmentProgressView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAssignmentProgressServiceTests {

    @Test
    void shouldAggregateLearnerProgressForTeacherOrLearnerParticipant() {
        LearningAssignment assignment = new LearningAssignment(
                "tenant-a", "teacher-1", "student-1", "函数作业", "完成练习",
                "数学", "高中一年级", "人教A版", "函数", 0.8, Instant.now().plusSeconds(3600));
        LearningGoal goal = new LearningGoal(
                "tenant-a", "student-1", "profile-1", "函数目标", "函数", 0.2, 0.8);
        assignment.accept("profile-1", goal.getId(), Instant.now());
        LearnerMastery mastery = new LearnerMastery("tenant-a", "profile-1", "函数", 0.5, 2, 1);

        LearningTask completed = new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                goal.getId(), "plan-1", 0, "复习函数", "复习定义", Instant.now());
        completed.start("conversation-1", "run-1", Instant.now());
        completed.complete(true, Instant.now());
        LearningTask awaiting = new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                goal.getId(), "plan-1", 1, "复习函数性质", "复习性质", Instant.now());
        awaiting.start("conversation-2", "run-2", Instant.now());
        awaiting.awaitEvidence(Instant.now());

        AssessmentAttempt first = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                goal.getId(), "profile-1", "函数", true, 0.5, 0.2, 0.5, "反馈");
        AssessmentAttempt second = new AssessmentAttempt("tenant-a", "student-1", "run-2", "step-2",
                goal.getId(), "profile-1", "函数", false, 0.4, 0.5, 0.4, "反馈");

        LearningAssignmentService assignments = mock(LearningAssignmentService.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository masteryRepository = mock(LearnerMasteryRepository.class);
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        org.mingharness.runtime.repository.RunRepository runs =
                mock(org.mingharness.runtime.repository.RunRepository.class);
        when(assignments.getForParticipant("tenant-a", "teacher-1", assignment.getId()))
                .thenReturn(assignment);
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(masteryRepository.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", "profile-1", "函数")).thenReturn(Optional.of(mastery));
        when(tasks.findByTenantIdAndUserIdAndLearningGoalIdOrderByScheduledAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(completed, awaiting));
        when(assessments.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(first, second));
        when(runs.countByTenantIdAndUserIdAndEducationLearningAssignmentId(
                "tenant-a", "student-1", assignment.getId())).thenReturn(2L);
        LearningAssignmentFeedback feedback = new LearningAssignmentFeedback(
                "tenant-a", assignment.getId(), "teacher-1", "student-1",
                LearningAssignmentFeedbackAction.REQUEST_EVIDENCE, "请补充作答依据", null, Instant.now());
        feedback.acknowledge(Instant.now());
        when(feedbacks.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                eq("tenant-a"), eq(assignment.getId()), any())).thenReturn(List.of(feedback));

        LearningAssignmentProgressView view = new LearningAssignmentProgressService(
                assignments, goals, masteryRepository, tasks, assessments, feedbacks, runs)
                .get("tenant-a", "teacher-1", assignment.getId());

        assertEquals("ACCEPTED", view.status());
        assertEquals(0.5, view.currentMastery(), 1e-9);
        assertEquals(0.5, view.masteryProgress(), 1e-9);
        assertEquals(2, view.assessmentTotal());
        assertEquals(1, view.correctAssessmentTotal());
        assertEquals(2, view.taskTotal());
        assertEquals(2, view.taskStarted());
        assertEquals(1, view.taskCompleted());
        assertEquals(1, view.taskAwaitingEvidence());
        assertEquals(0.3, view.masteryGain(), 1e-9);
        assertEquals(2, view.runTotal());
        assertEquals(1.0, view.runEvidenceCoverageRate(), 1e-9);
        assertEquals(1, view.feedbackTotal());
        assertEquals(1.0, view.feedbackAcknowledgementRate(), 1e-9);
        assertEquals(1, view.feedbackEvidenceRequests());
    }
}
