package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningGoalRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningGoalServiceTests {

    @Test
    void shouldCaptureBaselineMasteryWhenCreatingGoal() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", "掌握函数", "zh-CN");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", profile.getId(), "函数"))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", profile.getId(), "函数", 0.35, 2, 1)));
        when(goals.save(any(LearningGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningGoalService service = new LearningGoalService(goals, profiles, mastery,
                new SensitiveDataSanitizer());
        LearningGoal goal = service.create("tenant-a", "student-1",
                new LearningGoalRequest(profile.getId(), "掌握函数基础", "函数", 0.8));

        assertEquals(profile.getId(), goal.getLearnerProfileId());
        assertEquals(0.35, goal.getBaselineMastery());
        assertEquals(0.8, goal.getTargetMastery());
        assertEquals(LearningGoalStatus.ACTIVE, goal.getStatus());
    }

    @Test
    void shouldRejectTargetNotHigherThanCurrentMastery() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", profile.getId(), "函数"))
                .thenReturn(Optional.of(new LearnerMastery("tenant-a", profile.getId(), "函数", 0.8, 4, 3)));

        LearningGoalService service = new LearningGoalService(goals, profiles, mastery,
                new SensitiveDataSanitizer());

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.create(
                "tenant-a", "student-1",
                new LearningGoalRequest(profile.getId(), "函数", "函数", 0.8)));
        assertEquals("LEARNING_GOAL_TARGET_INVALID", exception.getCode());
    }

    @Test
    void shouldNotReactivateCompletedGoal() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));

        LearningGoalService service = new LearningGoalService(goals, profiles, mastery,
                new SensitiveDataSanitizer());

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.changeStatus(
                "tenant-a", "student-1", goal.getId(), "ACTIVE"));
        assertEquals("LEARNING_GOAL_STATUS_CONFLICT", exception.getCode());
    }

    @Test
    void shouldRejectDirectCompletionWithoutAssessmentEvidence() {
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));

        LearningGoalService service = new LearningGoalService(goals, profiles, mastery,
                new SensitiveDataSanitizer());
        var exception = assertThrows(org.mingharness.common.BusinessException.class,
                () -> service.changeStatus("tenant-a", "student-1", goal.getId(), "COMPLETED"));

        assertEquals("LEARNING_GOAL_EVIDENCE_REQUIRED", exception.getCode());
    }
}
