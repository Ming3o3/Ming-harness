package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.feedback.RunFeedback;
import org.mingharness.feedback.RunFeedbackRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningRecommendationServiceTests {

    @Test
    void shouldRecommendBaselineDiagnosisBeforeAnyAssessment() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of());
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(Optional.empty());

        var recommendation = new LearningRecommendationService(goals, attempts, mastery)
                .recommend("tenant-a", "student-1", goal.getId());

        assertEquals("DIAGNOSE", recommendation.nextActionType());
        assertEquals(0.0, recommendation.progressRatio());
        assertEquals(0, recommendation.attemptCount());
    }

    @Test
    void shouldRecommendExplanationAfterRecentIncorrectAttempt() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        AssessmentAttempt failed = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                goal.getId(), "profile-1", "函数", false, 0.25, 0.4, 0.25, "混淆定义域");
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(failed));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", "profile-1", "函数", 0.25, 2, 0)));

        var recommendation = new LearningRecommendationService(goals, attempts, mastery)
                .recommend("tenant-a", "student-1", goal.getId());

        assertEquals("EXPLAIN", recommendation.nextActionType());
        assertEquals(1, recommendation.attemptCount());
        assertEquals(0, recommendation.correctAttemptCount());
    }

    @Test
    void shouldTurnCodeDiagnosticEvidenceIntoTargetedDiagnosis() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握类型转换", "类型转换", 0.2, 0.8);
        AssessmentAttempt failedCode = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                goal.getId(), "profile-1", "类型转换", false, 0.2, 0.4, 0.32, "代码评测");
        failedCode.setStructuredEvidence(2, "[]", false, false, "CODE_TYPE");
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(failedCode));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "类型转换"))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", "profile-1", "类型转换", 0.32, 2, 0)));

        var recommendation = new LearningRecommendationService(goals, attempts, mastery)
                .recommend("tenant-a", "student-1", goal.getId());

        assertEquals("DIAGNOSE", recommendation.nextActionType());
        assertTrue(recommendation.nextActionTitle().contains("类型错误"));
        assertTrue(recommendation.nextActionPrompt().contains("独立测试"));
    }

    @Test
    void shouldUseNegativeRunFeedbackToAdjustTheNextAction() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        RunFeedbackRepository feedback = mock(RunFeedbackRepository.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        AssessmentAttempt successful = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                goal.getId(), "profile-1", "函数", true, 0.8, 0.4, 0.52, "完成");
        RunFeedback negative = mock(RunFeedback.class);
        when(negative.getRating()).thenReturn("NEGATIVE");
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(java.util.Optional.of(goal));
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(successful));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(java.util.Optional.of(new LearnerMastery("tenant-a", "profile-1", "函数", 0.52, 1, 1)));
        when(feedback.findByRunIdAndUserId("run-1", "student-1"))
                .thenReturn(java.util.Optional.of(negative));

        var recommendation = new LearningRecommendationService(goals, attempts, mastery, feedback)
                .recommend("tenant-a", "student-1", goal.getId());

        assertEquals("EXPLAIN", recommendation.nextActionType());
        assertEquals("根据反馈调整教学方式", recommendation.nextActionTitle());
    }

    @Test
    void shouldWaitUntilTheNextReviewDateAfterSuccessfulRetentionCheck() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningReviewPlanService reviewPlans = mock(LearningReviewPlanService.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", goal.getId(),
                "profile-1", "函数", java.time.Instant.now().plusSeconds(3600));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of());
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", "profile-1", "函数", 0.85, 3, 3)));
        when(reviewPlans.find("tenant-a", "student-1", goal.getId())).thenReturn(plan);

        var recommendation = new LearningRecommendationService(goals, attempts, mastery, null, reviewPlans)
                .recommend("tenant-a", "student-1", goal.getId());

        assertEquals("WAIT", recommendation.nextActionType());
        assertEquals(plan.getId(), recommendation.reviewPlanId());
        assertEquals(plan.getNextReviewAt(), recommendation.nextReviewAt());
    }

    @Test
    void shouldUseTheWeakestPrerequisiteForTheNextTeachingAction() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        EducationKnowledgeGraphService graph = mock(EducationKnowledgeGraphService.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握递归", "递归", 0.2, 0.8);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "编程",
                "大一", "课程版", "递归", "zh-CN");
        AssessmentAttempt failed = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1",
                goal.getId(), "profile-1", "递归", false, 0.2, 0.4, 0.28, "栈帧理解错误");
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(attempts.findByTenantIdAndUserIdAndLearningGoalIdOrderByCreatedAtAsc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(failed));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "递归"))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", "profile-1", "递归", 0.28, 1, 0)));
        when(profiles.findByIdAndTenantIdAndUserId("profile-1", "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", "profile-1"))
                .thenReturn(List.of());
        when(graph.resolve(any(), any(EducationRetrievalFilter.class))).thenReturn(
                new EducationDependencyGraph("递归", List.of(
                        new EducationDependencyPath("函数调用", 1, 0.7, 0.3),
                        new EducationDependencyPath("栈与状态", 2, 0.1, 0.9)), false));

        var recommendation = new LearningRecommendationService(goals, attempts, mastery, null, null,
                profiles, graph).recommend("tenant-a", "student-1", goal.getId());

        assertEquals("PREREQUISITE_REMEDIATION", recommendation.nextActionType());
        assertEquals("栈与状态", recommendation.priorityPrerequisiteConcept());
        assertEquals(0.9, recommendation.priorityPrerequisiteDeficit());
        assertTrue(recommendation.dependencyGraphAvailable());
        assertTrue(recommendation.nextActionPrompt().contains("栈与状态"));
    }
}
