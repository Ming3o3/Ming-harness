package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationAssessmentServiceTests {

    @Test
    void shouldPersistAttemptAndCompleteGoalWhenTargetIsReached() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);

        Run run = new Run("tenant-a", "student-1", "函数学习", "请帮助我掌握函数",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1", null,
                "education.read,education.write", true, 4);
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.6);
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", goal.getId(),
                goal.getTitle(), 0.2, 0.6, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20"));
        LearnerMastery previous = new LearnerMastery("tenant-a", "profile-1", "函数", 0.2, 1, 0);
        LearnerMastery updated = new LearnerMastery("tenant-a", "profile-1", "函数", 0.65, 2, 1);
        AssessmentAttempt saved = new AssessmentAttempt("tenant-a", "student-1", run.getId(), "step-1",
                goal.getId(), "profile-1", "函数", true, 1.0, 0.2, 0.65, "答题正确");

        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(Optional.of(previous));
        when(learnerService.updateMastery(any(), any(), any(), any())).thenReturn(updated);
        when(attempts.save(any(AssessmentAttempt.class))).thenReturn(saved);

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, new SensitiveDataSanitizer());
        AssessmentAttempt result = service.record("tenant-a", "student-1", run.getId(), "step-1",
                "profile-1", "函数", true, 1.0, "答题正确");

        assertSame(saved, result);
        assertEquals(0.2, result.getMasteryBefore());
        assertEquals(0.65, result.getMasteryAfter());
        assertEquals(LearningGoalStatus.COMPLETED, goal.getStatus());
    }
}
