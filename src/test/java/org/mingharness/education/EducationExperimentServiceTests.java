package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.education.api.EducationExperimentView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationExperimentServiceTests {

    @Test
    void shouldAggregateFrozenStrategyEvidenceAndLearningOutcome() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run run = new Run("tenant-a", "student-1", "函数学习", "求定义域", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(new EducationRunConfiguration(
                true, "profile-1", "goal-1", null, null, null, null, null,
                "掌握函数", 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20", null, null, null, "FULL"));
        Step step = new Step(1, StepType.MODEL, "model.complete", "求定义域");
        step.start();
        step.setContextEvidenceJson(ContextEvidenceSnapshotCodec.encode(List.of(
                new ContextEvidence("doc-1", "定义域", "document:doc-1#window:0",
                        "定义域内容", 0.9, "覆盖前置缺口", List.of("集合"),
                        new EducationRankingBreakdown(0.9, 1.0, 0.8, 0.7,
                                0.9, 1.0, 0.0, 0.88)),
                new ContextEvidence("doc-1", "定义域", "document:doc-1#window:0#chunk:1",
                        "重复内容", 0.8, "重复", List.of("集合"),
                        new EducationRankingBreakdown(0.8, 1.0, 0.8, 0.7,
                                0.9, 0.0, 1.0, 0.60))
        )));
        step.succeed("回答");
        run.addStep(step);

        AssessmentAttempt attempt = new AssessmentAttempt("tenant-a", "student-1", run.getId(),
                step.getId(), "goal-1", "profile-1", "函数", true,
                0.85, 0.20, 0.85, AssessmentAttemptType.FORMATIVE,
                null, "MODEL_TOOL", "学生推理", "正确");
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(List.of(run));
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(List.of(attempt));

        EducationExperimentView view = new EducationExperimentService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        var full = view.strategies().stream()
                .filter(item -> item.retrievalStrategy().equals("FULL"))
                .findFirst().orElseThrow();
        assertEquals(1, full.runCount());
        assertEquals(2.0, full.averageEvidenceCount());
        assertEquals(1.0, full.averageUniqueEvidenceCount());
        assertEquals(35.0, full.averageEvidenceChars(), 0.0001);
        assertTrue(full.averageUtilityPerThousandChars() > 0.0);
        assertTrue(full.averageMarginalCoveragePerThousandChars() > 0.0);
        assertEquals(0.5, full.evidenceRedundancyRate());
        assertEquals(1, full.assessmentCount());
        assertEquals(1.0, full.assessmentAccuracyRate());
        assertEquals(0.65, full.averageMasteryGain(), 0.0001);
        assertTrue(view.generatedAt() != null);
    }

    @Test
    void shouldKeepAllStrategiesInStableOrderForEmptyExperiment() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        when(runs.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of());
        when(assessments.findByTenantIdOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of());

        EducationExperimentView view = new EducationExperimentService(runs, assessments)
                .summarize("tenant-a", "operator", true);

        assertEquals(List.of("FULL", "VECTOR_ONLY", "KEYWORD_ONLY", "NO_LEARNER_STATE",
                        "NO_DEPENDENCY_GRAPH", "STATIC_WEIGHT", "CALIBRATED", "ADAPTIVE"),
                view.strategies().stream().map(item -> item.retrievalStrategy()).toList());
        assertEquals(0, view.totalRunCount());
        assertEquals("NO_DATA", view.strategies().get(0).sampleStatus());
        assertEquals(0, view.pairedLearnerGoalCount());
    }

    @Test
    void shouldExposeRequestedAndEffectiveStrategyAllocationSeparately() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run balanced = educationalRun("BALANCED_EXPERIMENT", 0.8);
        balanced.attachEducationRetrievalPolicy(EducationRetrievalPolicySnapshotCodec.encode(
                new EducationRetrievalPolicySnapshot(
                        EducationRetrievalPolicySnapshot.VERSION, "LOW_MASTERY_GAP_FIRST", "CALIBRATED", 0,
                        "按分配次数选择 CALIBRATED", List.of())));
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(List.of(balanced));
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(List.of());

        EducationExperimentView view = new EducationExperimentService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        var allocation = view.allocations().stream()
                .filter(item -> item.requestedStrategy().equals("BALANCED_EXPERIMENT"))
                .findFirst().orElseThrow();
        assertEquals("CALIBRATED", allocation.effectiveStrategy());
        assertEquals("LOW_MASTERY_GAP_FIRST", allocation.conditioning());
        assertEquals(1, allocation.allocationCount());
        assertEquals(1, allocation.successfulRunCount());
        assertEquals(0, allocation.outcomeRunCount());
        String csv = new EducationExperimentService(runs, assessments)
                .exportAllocationCsv("tenant-a", "student-1", false);
        assertTrue(csv.startsWith("requested_strategy,effective_strategy,conditioning,"));
        assertTrue(csv.contains("\"BALANCED_EXPERIMENT\",\"CALIBRATED\""));
    }

    @Test
    void shouldCompareLearningOutcomeOnlyForSameLearnerGoalAcrossStrategies() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run full = educationalRun("FULL", 0.8);
        Run vector = educationalRun("VECTOR_ONLY", 0.8);
        Step fullStep = full.getSteps().get(0);
        Step vectorStep = vector.getSteps().get(0);
        AssessmentAttempt fullAttempt = new AssessmentAttempt("tenant-a", "student-1", full.getId(),
                fullStep.getId(), "goal-1", "profile-1", "函数", false,
                0.50, 0.20, 0.50, AssessmentAttemptType.FORMATIVE,
                null, "MODEL_TOOL", "仍然混淆定义域", "需要补强");
        AssessmentAttempt vectorAttempt = new AssessmentAttempt("tenant-a", "student-1", vector.getId(),
                vectorStep.getId(), "goal-1", "profile-1", "函数", true,
                0.90, 0.20, 0.90, AssessmentAttemptType.FORMATIVE,
                null, "MODEL_TOOL", "能说明定义域限制", "达到目标");
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(List.of(full, vector));
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(List.of(fullAttempt, vectorAttempt));

        EducationExperimentView view = new EducationExperimentService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        var pair = view.pairedComparisons().stream()
                .filter(item -> item.comparedStrategy().equals("VECTOR_ONLY"))
                .findFirst().orElseThrow();
        assertEquals("FULL", pair.referenceStrategy());
        assertEquals(1, pair.pairedLearnerGoalCount());
        assertEquals(0.30, pair.referenceAverageMasteryGain(), 0.0001);
        assertEquals(0.70, pair.comparedAverageMasteryGain(), 0.0001);
        assertEquals(0.40, pair.masteryGainDelta(), 0.0001);
        assertEquals(0.0, pair.referenceTargetReachRate(), 0.0001);
        assertEquals(1.0, pair.comparedTargetReachRate(), 0.0001);
        assertEquals(1.0, pair.targetReachRateDelta(), 0.0001);
        assertEquals("INSUFFICIENT_SAMPLE", pair.sampleStatus());

        String csv = new EducationExperimentService(runs, assessments)
                .exportPairedCsv("tenant-a", "student-1", false);
        assertTrue(csv.startsWith("reference_strategy,compared_strategy,"));
        assertTrue(csv.contains("\"VECTOR_ONLY\""));
    }

    @Test
    void shouldEstimateJointStateGraphAblationOnlyForCompleteFourArmPairs() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run full = educationalRun("FULL", 0.8);
        Run noState = educationalRun("NO_LEARNER_STATE", 0.8);
        Run noGraph = educationalRun("NO_DEPENDENCY_GRAPH", 0.8);
        Run vector = educationalRun("VECTOR_ONLY", 0.8);
        List<Run> all = List.of(full, noState, noGraph, vector);
        List<AssessmentAttempt> outcomes = List.of(
                attempt(full, 0.80), attempt(noState, 0.40),
                attempt(noGraph, 0.50), attempt(vector, 0.20));
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(all);
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(outcomes);

        EducationExperimentView view = new EducationExperimentService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        assertEquals(1, view.jointAblation().fullyPairedLearnerGoalCount());
        assertEquals(0.60, view.jointAblation().fullAverageMasteryGain(), 0.0001);
        assertEquals(0.40, view.jointAblation().fullMinusNoLearnerState(), 0.0001);
        assertEquals(0.30, view.jointAblation().fullMinusNoDependencyGraph(), 0.0001);
        assertEquals(0.10, view.jointAblation().interactionEffect(), 0.0001);
        assertEquals("INSUFFICIENT_SAMPLE", view.jointAblation().sampleStatus());

        String csv = new EducationExperimentService(runs, assessments)
                .exportSynergyCsv("tenant-a", "student-1", false);
        assertTrue(csv.startsWith("fully_paired_learner_goal_count,"));
        assertTrue(csv.contains("\"INSUFFICIENT_SAMPLE\""));
    }

    private AssessmentAttempt attempt(Run run, double masteryAfter) {
        Step step = run.getSteps().get(0);
        return new AssessmentAttempt("tenant-a", "student-1", run.getId(), step.getId(),
                "goal-1", "profile-1", "函数", masteryAfter >= 0.5,
                masteryAfter, 0.20, masteryAfter, AssessmentAttemptType.FORMATIVE,
                null, "MODEL_TOOL", "学生作答", "反馈");
    }

    private Run educationalRun(String strategy, double target) {
        Run run = new Run("tenant-a", "student-1", "函数学习", "求定义域", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(new EducationRunConfiguration(
                true, "profile-1", "goal-1", null, null, null, null, null,
                "掌握函数", 0.2, target, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20", null, null, "", strategy));
        run.start();
        Step step = new Step(1, StepType.MODEL, "model.complete", "求定义域");
        step.start();
        step.succeed("回答");
        run.addStep(step);
        run.succeed("回答");
        return run;
    }
}
