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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

        AssessmentAttempt attempt = new EducationCodeEvidenceService(attempts, runs, mastery, learner).record(
                "tenant-a", "student-1", assignment, run,
                new EducationCodeEvaluationResult(CodeEvaluationStatus.PASSED,
                        "语法/编译检查通过。", "", 0, 25));

        assertEquals("CODE_NONE", attempt.getQuestionType());
        verify(learner).recordObservedMasteryWithoutGoalCompletion(
                any(), any(), any(), any(MasteryUpdateRequest.class));
        verify(attempts).save(any(AssessmentAttempt.class));
    }

    @Test
    void routesBehaviorEvidenceToAnnotatedKnowledgePoints() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learner = mock(EducationLearnerService.class);
        LearningAssignment assignment = assignment();
        Run run = successfulRunWithTestCases(assignment);
        when(learner.recordObservedMasteryWithoutGoalCompletion(
                any(), any(), any(), any(MasteryUpdateRequest.class), any()))
                .thenAnswer(invocation -> {
                    MasteryUpdateRequest request = invocation.getArgument(3);
                    return new LearnerMastery("tenant-a", "profile-1", request.conceptKey(),
                            request.effectiveMasteryScore(), 1,
                            Boolean.TRUE.equals(request.correct()) ? 1 : 0);
                });
        when(attempts.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAttempt attempt = new EducationCodeEvidenceService(attempts, runs, mastery, learner).record(
                "tenant-a", "student-1", assignment, run,
                new EducationCodeEvaluationResult(CodeEvaluationStatus.FAILED,
                        "部分用例失败", "", 1, 25,
                        List.of(
                                new EducationCodeTestCaseResult("loop-basic", CodeBehaviorEvaluationStatus.PASSED,
                                        "1", "1", "通过", 5),
                                new EducationCodeTestCaseResult("list-edge", CodeBehaviorEvaluationStatus.FAILED,
                                        "0", "1", "输出不匹配", 5))));

        assertEquals("loops", attempt.getConceptKey());
        org.mockito.Mockito.verify(learner).recordObservedMasteryWithoutGoalCompletion(
                eq("tenant-a"), eq("student-1"), eq("profile-1"),
                org.mockito.ArgumentMatchers.argThat(request -> request.conceptKey().equals("loops")), any());
        org.mockito.Mockito.verify(learner).recordObservedMasteryWithoutGoalCompletion(
                eq("tenant-a"), eq("student-1"), eq("profile-1"),
                org.mockito.ArgumentMatchers.argThat(request -> request.conceptKey().equals("lists")), any());
        org.mockito.Mockito.verify(attempts).save(any(AssessmentAttempt.class));
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

    private Run successfulRunWithTestCases(LearningAssignment assignment) {
        Run run = successfulRun(assignment);
        EducationRunConfiguration base = run.educationConfiguration();
        String snapshot = EducationProgrammingTestCaseSnapshotCodec.encode(List.of(
                new LearningAssignmentTestCase("tenant-a", assignment.getId(), "loop-basic", "loops",
                        "基础循环", "1", "1", false, 1.0, 0),
                new LearningAssignmentTestCase("tenant-a", assignment.getId(), "list-edge", "lists",
                        "列表边界", "0", "1", false, 1.0, 1)));
        run.attachEducationConfiguration(base.withProgrammingTestCasesSnapshot(snapshot));
        return run;
    }
}
