package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.feedback.RunFeedback;
import org.mingharness.feedback.RunFeedbackRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
