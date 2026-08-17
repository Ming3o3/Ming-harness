package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EducationCodeEvidenceServiceTests {

    @Test
    void recordsLowWeightEvidenceWithoutCompletingTheLearningGoalDirectly() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learner = mock(EducationLearnerService.class);
        LearningAssignment assignment = assignment();
        Run run = successfulRun(assignment);
        LearnerMastery before = new LearnerMastery("tenant-a", "profile-1", "loops", 0.4, 1, 0);
        LearnerMastery after = new LearnerMastery("tenant-a", "profile-1", "loops", 0.52, 2, 1);
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", "profile-1", "loops")).thenReturn(Optional.of(before));
        when(learner.recordObservedMasteryWithoutGoalCompletion(
                any(), any(), any(), any(MasteryUpdateRequest.class))).thenReturn(after);
        when(attempts.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new EducationCodeEvidenceService(attempts, runs, mastery, learner).record(
                "tenant-a", "student-1", assignment, run,
                new EducationCodeEvaluationResult(CodeEvaluationStatus.PASSED,
                        "语法/编译检查通过。", "", 0, 25));

        verify(learner).recordObservedMasteryWithoutGoalCompletion(
                any(), any(), any(), any(MasteryUpdateRequest.class));
        verify(attempts).save(any(AssessmentAttempt.class));
    }

    private LearningAssignment assignment() {
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "编程作业", "完成循环练习", "编程", "大一", "课程版", "loops", 0.8,
                Instant.now().plusSeconds(3600));
        assignment.accept("profile-1", "goal-1", Instant.now());
        return assignment;
    }

    private Run successfulRun(LearningAssignment assignment) {
        Run run = new Run("tenant-a", "student-1", "作业", "输入", BigDecimal.ONE,
                "demo-model", "prompt-v1", "policy-v1");
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", "goal-1",
                assignment.getId(), assignment.getTitle(), assignment.getInstructions(), null,
                null, assignment.getTitle(), 0.2, 0.8, assignment.getSubject(),
                assignment.getGradeLevel(), assignment.getCurriculumVersion(), assignment.getConceptKey(),
                null, null, "PRACTICE", ""));
        run.addStep(new Step(1, StepType.MODEL, "教学", "循环"));
        run.start();
        run.succeed("完成");
        return run;
    }
}
