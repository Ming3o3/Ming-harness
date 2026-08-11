package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        Run run = new Run("tenant-a", "student-1", "函数学习", "我的作答：函数的自变量不能为零。",
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
        when(learnerService.recordObservedMastery(any(), any(), any(), any())).thenReturn(updated);
        when(attempts.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, new SensitiveDataSanitizer());
        AssessmentAttempt result = service.record("tenant-a", "student-1", run.getId(), "step-1",
                "profile-1", "函数", true, 1.0, "MODEL_TOOL",
                "学生正确说明了函数自变量的取值范围", "函数的自变量不能为零", "答题正确");

        assertEquals(0.2, result.getMasteryBefore());
        assertEquals(0.65, result.getMasteryAfter());
        assertEquals("函数的自变量不能为零", result.getLearnerEvidenceQuote());
        assertEquals(LearningGoalStatus.COMPLETED, goal.getStatus());
    }

    @Test
    void shouldRejectModelAssessmentWithoutLearnerEvidence() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);
        Run run = new Run("tenant-a", "student-1", "函数学习", "请帮助我掌握函数",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, new SensitiveDataSanitizer());

        var error = assertThrows(org.mingharness.common.BusinessException.class, () -> service.record(
                "tenant-a", "student-1", run.getId(), "step-1", "profile-1", "函数", true,
                1.0, "MODEL_TOOL", null, "模型推断正确"));

        assertEquals("ASSESSMENT_EVIDENCE_REQUIRED", error.getCode());
    }

    @Test
    void shouldRejectModelAssessmentWithoutLearnerEvidenceQuote() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);
        Run run = new Run("tenant-a", "student-1", "函数学习", "我的作答：函数的自变量不能为零",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, new SensitiveDataSanitizer());

        var error = assertThrows(org.mingharness.common.BusinessException.class, () -> service.record(
                "tenant-a", "student-1", run.getId(), "step-1", "profile-1", "函数", true,
                1.0, "MODEL_TOOL", "学生说明了定义域", "答题正确"));

        assertEquals("ASSESSMENT_LEARNER_EVIDENCE_QUOTE_REQUIRED", error.getCode());
    }

    @Test
    void shouldRejectModelAssessmentWhoseQuoteDoesNotBelongToRunInput() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);
        Run run = new Run("tenant-a", "student-1", "函数学习", "我的作答：函数的自变量不能为零",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, new SensitiveDataSanitizer());

        var error = assertThrows(org.mingharness.common.BusinessException.class, () -> service.record(
                "tenant-a", "student-1", run.getId(), "step-1", "profile-1", "函数", true,
                1.0, "MODEL_TOOL", "学生说明了定义域", "函数在定义域内连续", "答题正确"));

        assertEquals("ASSESSMENT_LEARNER_EVIDENCE_QUOTE_MISMATCH", error.getCode());
    }

    @Test
    void shouldPersistManualReviewEvidenceOnlyForFinishedRunStep() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);
        Run run = new Run("tenant-a", "student-1", "函数复核", "请复核函数",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.9);
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", goal.getId(),
                goal.getTitle(), 0.2, 0.9, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20"));
        Step step = new Step(1, StepType.MODEL, "模型复核", "题目");
        run.addStep(step);
        step.setContextEvidenceJson("[{\"documentId\":\"doc-1\",\"title\":\"函数教材\","
                + "\"citation\":\"document:doc-1#chunk:0\",\"excerpt\":\"函数定义域\"}]");
        run.start();
        step.start();
        step.succeed("已完成");
        run.succeed("已完成");
        LearnerMastery previous = new LearnerMastery("tenant-a", "profile-1", "函数", 0.2, 1, 0);
        LearnerMastery updated = new LearnerMastery("tenant-a", "profile-1", "函数", 0.5, 2, 1);

        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(Optional.of(previous));
        when(learnerService.recordObservedMastery(any(), any(), any(), any())).thenReturn(updated);
        when(attempts.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, new SensitiveDataSanitizer());
        AssessmentAttempt result = service.record("tenant-a", "student-1", run.getId(), step.getId(),
                "profile-1", "函数", true, 1.0, "MANUAL_REVIEW", "学生写出了定义域判定依据", "复核通过");

        assertEquals("MANUAL_REVIEW", result.getEvidenceSource());
        assertEquals("学生写出了定义域判定依据", result.getEvidenceText());
        assertEquals("复核通过", result.getFeedback());
        assertEquals(1, EducationRetrievalEvidence.decode(result.getRetrievalEvidenceJson()).size());
    }

    @Test
    void shouldRecordACompletedGoalReviewAgainstItsPlan() {
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        RunRepository runs = mock(RunRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationLearnerService learnerService = mock(EducationLearnerService.class);
        LearningReviewPlanService reviewPlans = mock(LearningReviewPlanService.class);
        Run run = new Run("tenant-a", "student-1", "函数保持度复习", "请复习函数",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", "profile-1",
                "掌握函数", "函数", 0.2, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", goal.getId(),
                "profile-1", "函数", java.time.Instant.now().minusSeconds(1));
        run.attachEducationConfiguration(new EducationRunConfiguration(true, "profile-1", goal.getId(),
                plan.getId(), goal.getTitle(), 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.85"));
        Step step = new Step(1, StepType.MODEL, "复习题", "题目");
        run.addStep(step);
        run.start();
        step.start();
        step.succeed("已完成");
        run.succeed("已完成");
        LearnerMastery previous = new LearnerMastery("tenant-a", "profile-1", "函数", 0.85, 3, 3);
        LearnerMastery updated = new LearnerMastery("tenant-a", "profile-1", "函数", 0.895, 4, 4);

        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(reviewPlans.getById("tenant-a", "student-1", plan.getId())).thenReturn(plan);
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey("tenant-a", "profile-1", "函数"))
                .thenReturn(Optional.of(previous));
        when(learnerService.recordObservedMastery(any(), any(), any(), any())).thenReturn(updated);
        when(attempts.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewPlans.recordReview(org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("student-1"),
                org.mockito.ArgumentMatchers.eq(plan.getId()),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any()))
                .thenReturn(plan);

        EducationAssessmentService service = new EducationAssessmentService(attempts, runs, goals, mastery,
                learnerService, reviewPlans, new SensitiveDataSanitizer());
        AssessmentAttempt result = service.record("tenant-a", "student-1", run.getId(), step.getId(),
                "profile-1", "函数", true, 1.0, "MANUAL_REVIEW", "学生完成迁移题并写出依据", "复习通过");

        assertEquals(AssessmentAttemptType.REVIEW, result.getAssessmentType());
        assertEquals(plan.getId(), result.getReviewPlanId());
        org.mockito.Mockito.verify(reviewPlans).recordReview(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("student-1"),
                org.mockito.ArgumentMatchers.eq(plan.getId()),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any());
    }
}
