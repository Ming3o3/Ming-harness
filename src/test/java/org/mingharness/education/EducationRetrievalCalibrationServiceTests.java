package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.context.api.EducationRankingWeights;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        EducationRetrievalCalibrationSnapshot source = new EducationRetrievalCalibrationSnapshot(
                EducationRetrievalCalibrationSnapshot.VERSION, 8, 4.5, 3.5, 4.0, 4.25,
                EducationRankingWeights.calibrated(4.5, 3.5, 4.0, 4.25, 8));

        EducationRetrievalCalibrationSnapshot decoded = EducationRetrievalCalibrationSnapshotCodec.decode(
                EducationRetrievalCalibrationSnapshotCodec.encode(source));

        assertEquals(source.version(), decoded.version());
        assertEquals(source.sampleCount(), decoded.sampleCount());
        assertEquals(source.weights().conditioning(), decoded.weights().conditioning());
        assertEquals(source.weights().targetConceptMatch(), decoded.weights().targetConceptMatch(), 0.000001);
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
}
