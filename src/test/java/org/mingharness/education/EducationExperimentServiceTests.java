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

        assertEquals(List.of("FULL", "VECTOR_ONLY", "KEYWORD_ONLY", "NO_LEARNER_STATE", "STATIC_WEIGHT"),
                view.strategies().stream().map(item -> item.retrievalStrategy()).toList());
        assertEquals(0, view.totalRunCount());
        assertEquals("NO_DATA", view.strategies().get(0).sampleStatus());
    }
}
