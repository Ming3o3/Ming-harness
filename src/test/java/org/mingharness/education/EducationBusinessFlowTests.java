package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.ConversationMessageView;
import org.mingharness.conversation.api.ConversationSummary;
import org.mingharness.education.api.ExecuteLearningActionRequest;
import org.mingharness.education.api.LearningAssignmentReviewRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 一条可重复的课程作业业务验收：布置、启动、测评达标、反馈确认。 */
class EducationBusinessFlowTests {

    @Test
    void shouldCloseAssignmentFromTeacherInstructionToMeasuredCompletionAndResolvedFeedback() {
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningAssignmentNotificationService assignmentNotifications =
                mock(LearningAssignmentNotificationService.class);
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        LearningAssignmentNotificationService notifications = mock(LearningAssignmentNotificationService.class);
        EducationActionService actions = mock(EducationActionService.class);
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);

        LearningAssignment assignment = new LearningAssignment(
                "tenant-a", "teacher-1", "student-1", "函数定义域作业", "完成题目并写出判定依据",
                "数学", "高中一年级", "人教A版", "函数定义域", 0.8,
                Instant.now().plusSeconds(3600));
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", assignment.getTitle(), "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                assignment.getTitle(), assignment.getConceptKey(), 0.2, 0.8);

        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(profiles.findByTenantIdAndUserIdAndSubjectAndGradeLevelAndCurriculumVersion(
                "tenant-a", "student-1", "数学", "高中一年级", "人教A版"))
                .thenReturn(Optional.of(profile));
        when(profiles.save(any(LearnerProfile.class))).thenReturn(profile);
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", profile.getId(), assignment.getConceptKey())).thenReturn(Optional.empty());
        when(goals.save(any(LearningGoal.class))).thenReturn(goal);
        when(assignments.save(any(LearningAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(actions.execute(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(detail("conversation-1"));

        LearningAssignmentService assignmentService = new LearningAssignmentService(
                assignments, profiles, goals, mastery, new SensitiveDataSanitizer(), assignmentNotifications);
        LearningAssignmentStartService startService = new LearningAssignmentStartService(assignmentService, actions);
        var started = startService.start("tenant-a", "student-1", assignment.getId(),
                new ExecuteLearningActionRequest(null, null, 4),
                "education.read,education.write", "assignment-start-1");
        assertEquals("ACCEPTED", started.assignment().status());

        LearningAssignmentFeedback feedback = new LearningAssignmentFeedback(
                "tenant-a", assignment.getId(), "teacher-1", "student-1",
                LearningAssignmentFeedbackAction.REQUEST_EVIDENCE,
                "请补充定义域判定依据", null, Instant.now());
        when(feedbacks.save(any(LearningAssignmentFeedback.class))).thenReturn(feedback);
        when(feedbacks.findByTenantIdAndLearningAssignmentIdAndId(
                "tenant-a", assignment.getId(), feedback.getId())).thenReturn(Optional.of(feedback));
        when(feedbacks.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                anyString(), anyString(), any())).thenReturn(List.of(feedback));
        LearningAssignmentFeedbackService feedbackService = new LearningAssignmentFeedbackService(
                feedbacks, assignmentService, assignments, notifications, new SensitiveDataSanitizer());
        feedbackService.create("tenant-a", "teacher-1", assignment.getId(),
                new org.mingharness.education.api.LearningAssignmentFeedbackRequest(
                        "REQUEST_EVIDENCE", feedback.getMessage(), null));
        assertEquals(LearningAssignmentFeedbackStatus.OPEN, feedback.getStatus());

        Run run = new Run("tenant-a", "student-1", assignment.getTitle(), "完成作业",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1", null,
                "education.read,education.write", true, 4);
        run.attachEducationConfiguration(new EducationRunConfiguration(true, profile.getId(), goal.getId(),
                assignment.getId(), assignment.getTitle(), assignment.getInstructions(), null,
                goal.getTitle(), 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数定义域",
                null, null, "PRACTICE", "函数定义域=0.20"));
        Step step = new Step(1, StepType.MODEL, "模型教学", "题目");
        run.addStep(step);
        run.start();
        step.start();
        step.succeed("学生写出判定依据");
        run.succeed("完成");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        LearnerMastery updated = new LearnerMastery("tenant-a", profile.getId(),
                assignment.getConceptKey(), 0.85, 2, 2);
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", profile.getId(), assignment.getConceptKey()))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", profile.getId(),
                        assignment.getConceptKey(), 0.2, 1, 0)));
        when(learnerService.recordObservedMastery(anyString(), anyString(), anyString(), any()))
                .thenReturn(updated);
        when(attempts.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignments.findByTenantIdAndLearningGoalId("tenant-a", goal.getId()))
                .thenReturn(List.of(assignment));

        LearningAssignmentCompletionService completionService = new LearningAssignmentCompletionService(
                assignments, assignmentNotifications);
        EducationAssessmentService assessmentService = new EducationAssessmentService(
                attempts, runs, goals, mastery, learnerService, null, null,
                completionService, new SensitiveDataSanitizer(), feedbackService);
        assessmentService.record("tenant-a", "student-1", run.getId(), step.getId(),
                profile.getId(), assignment.getConceptKey(), true, 0.85,
                "MANUAL_REVIEW", "学生写出定义域判定依据", "证据充分");
        assertEquals(LearningAssignmentStatus.COMPLETED, assignment.getStatus());
        assertEquals(LearningAssignmentFeedbackStatus.RESOLVED, feedback.getStatus());

        LearningAssignmentReviewService reviewService = new LearningAssignmentReviewService(
                assignments, assignmentNotifications, new SensitiveDataSanitizer());
        var reviewed = reviewService.review("tenant-a", "teacher-1", assignment.getId(),
                new LearningAssignmentReviewRequest("VERIFY", "已确认作答依据"));
        assertEquals("VERIFIED", reviewed.reviewStatus());

    }

    private ConversationDetail detail(String conversationId) {
        ConversationSummary summary = new ConversationSummary(conversationId, "tenant-a", "student-1",
                "函数定义域作业", null, Instant.now(), Instant.now(), 0, null, null);
        return new ConversationDetail(summary, List.<ConversationMessageView>of());
    }
}
