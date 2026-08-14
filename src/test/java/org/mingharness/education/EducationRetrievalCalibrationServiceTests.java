package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.context.api.EducationRankingWeights;
import org.mingharness.runtime.domain.Run;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class EducationRetrievalCalibrationServiceTests {

    @Test
    void shouldUseLatestJudgmentPerEvaluatorEvidenceAndShrinkWeights() {
        EducationRetrievalJudgmentRepository repository = mock(EducationRetrievalJudgmentRepository.class);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = first.plusSeconds(10);
        EducationRetrievalJudgment old = judgment("run-1", "step-1", "teacher-1", 1, 1, 1, 1, first);
        EducationRetrievalJudgment latest = judgment("run-1", "step-1", "teacher-1", 5, 4, 3, 5, second);
        EducationRetrievalJudgment other = judgment("run-2", "step-1", "teacher-2", 3, 2, 5, 3,
                second.plusSeconds(10));
        when(repository.findByTenantIdOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of(old, latest, other));

        EducationRetrievalCalibrationSnapshot snapshot = new EducationRetrievalCalibrationService(repository)
                .snapshotForTenant("tenant-a");

        assertEquals(EducationRetrievalCalibrationSnapshot.VERSION, snapshot.version());
        assertEquals(2, snapshot.sampleCount());
        assertEquals(4.0, snapshot.targetGroundingMean(), 0.000001);
        assertEquals(3.0, snapshot.prerequisiteUtilityMean(), 0.000001);
        assertEquals(4.0, snapshot.difficultyFitMean(), 0.000001);
        assertEquals(4.0, snapshot.overallUtilityMean(), 0.000001);
        assertTrue(snapshot.weights().conditioning().startsWith("CALIBRATED_V1:n=2"));
        assertEquals(1.0, total(snapshot.weights()), 0.000001);
    }

    @Test
    void shouldRoundTripVersionedSnapshot() {
        EducationRetrievalCalibrationSlice slice = new EducationRetrievalCalibrationSlice(
                "LOW_MASTERY_GAP_FIRST", 8, 4.5, 3.5, 4.0, 4.25,
                EducationRankingWeights.calibrated(4.5, 3.5, 4.0, 4.25, 8)
                        .withConditioning("CALIBRATED_V2:LOW_MASTERY_GAP_FIRST:n=8"),
                12, 0.25, 0.75, 0.50, 0.75);
        EducationRetrievalCalibrationSnapshot source = new EducationRetrievalCalibrationSnapshot(
                EducationRetrievalCalibrationSnapshot.VERSION, 8, 4.5, 3.5, 4.0, 4.25,
                EducationRankingWeights.calibrated(4.5, 3.5, 4.0, 4.25, 8),
                Map.of("LOW_MASTERY_GAP_FIRST", slice));

        EducationRetrievalCalibrationSnapshot decoded = EducationRetrievalCalibrationSnapshotCodec.decode(
                EducationRetrievalCalibrationSnapshotCodec.encode(source));

        assertEquals(source.version(), decoded.version());
        assertEquals(source.sampleCount(), decoded.sampleCount());
        assertEquals(source.weights().conditioning(), decoded.weights().conditioning());
        assertEquals(source.weights().targetConceptMatch(), decoded.weights().targetConceptMatch(), 0.000001);
        assertEquals(1, decoded.stateSlices().size());
        assertEquals("CALIBRATED_V2:LOW_MASTERY_GAP_FIRST:n=8",
                decoded.stateSlices().get("LOW_MASTERY_GAP_FIRST").weights().conditioning());
        assertEquals(12, decoded.stateSlices().get("LOW_MASTERY_GAP_FIRST").outcomeAssessmentCount());
        assertEquals(0.75, decoded.stateSlices().get("LOW_MASTERY_GAP_FIRST").outcomeScore(), 0.000001);
    }

    @Test
    void shouldFallBackToExplicitPriorWithoutTeacherFacts() {
        EducationRetrievalJudgmentRepository repository = mock(EducationRetrievalJudgmentRepository.class);
        when(repository.findByTenantIdOrderByCreatedAtAsc("tenant-a")).thenReturn(List.of());

        EducationRetrievalCalibrationSnapshot snapshot = new EducationRetrievalCalibrationService(repository)
                .snapshotForTenant("tenant-a");

        assertEquals(0, snapshot.sampleCount());
        assertEquals("CALIBRATED_PRIOR", snapshot.weights().conditioning());
    }

    @Test
    void shouldBuildStateConditionedSlicesFromFrozenRunMastery() {
        EducationRetrievalJudgmentRepository repository = mock(EducationRetrievalJudgmentRepository.class);
        org.mingharness.runtime.repository.RunRepository runs = mock(
                org.mingharness.runtime.repository.RunRepository.class);
        Run lowMastery = runWithMastery("函数=0.10");
        Run highMastery = runWithMastery("函数=0.90");
        List<EducationRetrievalJudgment> judgments = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            judgments.add(judgment(lowMastery.getId(), "step-1", "teacher-low-" + index,
                    5, 5, 2, 5, Instant.parse("2026-02-01T00:00:0" + index + "Z")));
            judgments.add(judgment(highMastery.getId(), "step-1", "teacher-high-" + index,
                    2, 2, 5, 2, Instant.parse("2026-02-01T00:01:0" + index + "Z")));
        }
        when(repository.findByTenantIdOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(judgments);
        when(runs.findByTenantIdAndIdIn(eq("tenant-a"), anyList()))
                .thenReturn(List.of(lowMastery, highMastery));

        EducationRetrievalCalibrationSnapshot snapshot = new EducationRetrievalCalibrationService(
                repository, runs).snapshotForTenant("tenant-a");

        assertEquals(2, snapshot.stateSlices().size());
        EducationRetrievalCalibrationSlice lowSlice = snapshot.stateSlices()
                .get("LOW_MASTERY_GAP_FIRST");
        EducationRetrievalCalibrationSlice highSlice = snapshot.stateSlices()
                .get("HIGH_MASTERY_TARGET_FIRST");
        assertEquals(5, lowSlice.sampleCount());
        assertEquals(5, highSlice.sampleCount());
        assertTrue(lowSlice.weights().conditioning().startsWith("CALIBRATED_V2:LOW_MASTERY_GAP_FIRST"));
        assertTrue(highSlice.weights().conditioning().startsWith("CALIBRATED_V2:HIGH_MASTERY_TARGET_FIRST"));
        EducationRankingWeights snapshotLowWeights = snapshot.weightsFor("LOW_MASTERY_GAP_FIRST");
        assertEquals(lowSlice.weights().conditioning(), snapshotLowWeights.conditioning());
        assertWeightsClose(lowSlice.weights(), snapshotLowWeights);
        String encoded = EducationRetrievalCalibrationSnapshotCodec.encode(snapshot);
        EducationRankingWeights selected = new EducationRetrievalCalibrationService(repository, runs)
                .weightsFromSnapshot(encoded, lowMastery.educationConfiguration());
        assertEquals(lowSlice.weights().conditioning(), selected.conditioning());
        assertWeightsClose(lowSlice.weights(), selected);
    }

    @Test
    void shouldBlendFormativeOutcomeIntoStateConditionedCalibration() {
        EducationRetrievalJudgmentRepository repository = mock(EducationRetrievalJudgmentRepository.class);
        org.mingharness.runtime.repository.RunRepository runs = mock(
                org.mingharness.runtime.repository.RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run lowMastery = runWithMastery("函数=0.10");
        lowMastery.start();
        lowMastery.succeed("完成");
        List<EducationRetrievalJudgment> judgments = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            judgments.add(judgment(lowMastery.getId(), "step-1", "teacher-low-" + index,
                    4, 4, 3, 4, Instant.parse("2026-03-01T00:00:0" + index + "Z")));
        }
        when(repository.findByTenantIdOrderByCreatedAtAsc("tenant-a")).thenReturn(judgments);
        when(runs.findByTenantIdAndIdIn(eq("tenant-a"), anyList())).thenReturn(List.of(lowMastery));
        when(runs.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of(lowMastery));
        when(assessments.findByTenantIdOrderByCreatedAtAsc("tenant-a")).thenReturn(List.of(
                new AssessmentAttempt("tenant-a", "student-1", lowMastery.getId(), "step-1",
                        "goal-1", "profile-1", "函数", true, 0.85, 0.10, 0.80, "证据", "作答正确", "通过"),
                new AssessmentAttempt("tenant-a", "student-1", lowMastery.getId(), "step-1",
                        "goal-1", "profile-1", "函数", true, 0.90, 0.80, 0.90, "证据", "迁移正确", "保持")));

        EducationRetrievalCalibrationSlice slice = new EducationRetrievalCalibrationService(
                repository, runs, assessments).snapshotForTenant("tenant-a")
                .stateSlices().get("LOW_MASTERY_GAP_FIRST");

        assertEquals(2, slice.outcomeAssessmentCount());
        assertEquals(0.40, slice.outcomeMasteryGainMean(), 0.000001);
        assertEquals(1.0, slice.outcomeCorrectRate(), 0.000001);
        assertEquals(1.0, slice.outcomeTargetReachRate(), 0.000001);
        assertTrue(slice.outcomeScore() > 0.75);
        assertTrue(slice.weights().retrievalRelevance() > EducationRankingWeights
                .calibrated(4.0, 4.0, 3.0, 4.0, 5).retrievalRelevance());
    }

    private void assertWeightsClose(EducationRankingWeights expected,
                                    EducationRankingWeights actual) {
        assertEquals(expected.retrievalRelevance(), actual.retrievalRelevance(), 0.000001);
        assertEquals(expected.targetConceptMatch(), actual.targetConceptMatch(), 0.000001);
        assertEquals(expected.prerequisiteGap(), actual.prerequisiteGap(), 0.000001);
        assertEquals(expected.graphCoverage(), actual.graphCoverage(), 0.000001);
        assertEquals(expected.difficultyFit(), actual.difficultyFit(), 0.000001);
    }

    private EducationRetrievalJudgment judgment(String runId, String stepId, String evaluator,
                                                 int target, int prerequisite, int difficulty,
                                                 int overall, Instant createdAt) {
        return new EducationRetrievalJudgment("tenant-a", runId, stepId, evaluator, "doc-1",
                "document:doc-1#window:0", target, prerequisite, difficulty, overall, null, createdAt);
    }

    private double total(EducationRankingWeights weights) {
        return weights.retrievalRelevance() + weights.targetConceptMatch()
                + weights.prerequisiteGap() + weights.graphCoverage() + weights.difficultyFit();
    }

    private Run runWithMastery(String masterySummary) {
        Run run = new Run("tenant-a", "student-1", "函数学习", "求定义域", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(new EducationRunConfiguration(
                true, "profile-1", "goal-1", null, null, null, null, null,
                "掌握函数", 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", masterySummary, null, null, "", "FULL"));
        return run;
    }
}
