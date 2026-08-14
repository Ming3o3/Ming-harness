package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationRetrievalPolicyServiceTests {

    @Test
    void shouldChooseStateConditionedStrategyWithHigherShrunkLearningOutcome() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        EducationRetrievalCalibrationService calibration = mock(EducationRetrievalCalibrationService.class);
        EducationRunConfiguration current = configuration("ADAPTIVE", "函数=0.10");
        when(calibration.conditioningFor(any(EducationRunConfiguration.class)))
                .thenReturn("LOW_MASTERY_GAP_FIRST");
        when(calibration.conditioningFor(any(Run.class))).thenReturn("LOW_MASTERY_GAP_FIRST");

        List<Run> historicalRuns = new ArrayList<>();
        List<AssessmentAttempt> historicalAttempts = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            Run full = completedRun("FULL", "函数=0.10");
            historicalRuns.add(full);
            historicalAttempts.add(attempt(full, false, 0.10, 0.20));
            Run calibrated = completedRun("CALIBRATED", "函数=0.10");
            historicalRuns.add(calibrated);
            historicalAttempts.add(attempt(calibrated, true, 0.10, 0.85));
        }
        when(runs.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(historicalRuns);
        when(assessments.findByTenantIdOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(historicalAttempts);

        EducationRetrievalPolicySnapshot snapshot = new EducationRetrievalPolicyService(
                runs, assessments, calibration).snapshotFor("tenant-a", "student-1", current);

        assertEquals("LOW_MASTERY_GAP_FIRST", snapshot.conditioning());
        assertEquals("CALIBRATED", snapshot.selectedStrategy());
        assertEquals(10, snapshot.eligibleRunCount());
        assertTrue(snapshot.selectionReason().contains("CALIBRATED"));
        assertEquals(4, snapshot.candidates().size());
        assertTrue(snapshot.candidates().stream()
                .filter(item -> item.strategy().equals("CALIBRATED"))
                .findFirst().orElseThrow().adjustedScore() > 0.7);
    }

    @Test
    void shouldFreezeAndDecodeAdaptivePolicySnapshot() {
        EducationRetrievalPolicySnapshot source = new EducationRetrievalPolicySnapshot(
                EducationRetrievalPolicySnapshot.VERSION, "MID_MASTERY_BALANCED", "FULL", 6,
                "样本量不足，使用收缩后的候选分数选择 FULL",
                List.of(new EducationRetrievalPolicyCandidate("FULL", 3, 6, 0.2,
                        0.8, 0.5, 0.75, 0.375, 0.59375, "INSUFFICIENT_SAMPLE")));

        EducationRetrievalPolicySnapshot decoded = EducationRetrievalPolicySnapshotCodec.decode(
                EducationRetrievalPolicySnapshotCodec.encode(source));

        assertEquals(source.version(), decoded.version());
        assertEquals(source.conditioning(), decoded.conditioning());
        assertEquals(source.selectedStrategy(), decoded.selectedStrategy());
        assertEquals(0.59375, decoded.candidates().get(0).adjustedScore(), 0.000001);
    }

    @Test
    void shouldFallBackToFullWhenNoHistoricalEvidenceExists() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        EducationRetrievalCalibrationService calibration = mock(EducationRetrievalCalibrationService.class);
        when(calibration.conditioningFor(any(EducationRunConfiguration.class)))
                .thenReturn("HIGH_MASTERY_TARGET_FIRST");
        when(runs.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of());
        when(assessments.findByTenantIdOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of());

        EducationRetrievalPolicySnapshot snapshot = new EducationRetrievalPolicyService(
                runs, assessments, calibration).snapshotFor("tenant-a", "student-1",
                configuration("ADAPTIVE", "函数=0.90"));

        assertEquals("FULL", snapshot.selectedStrategy());
        assertEquals(0, snapshot.eligibleRunCount());
        assertTrue(snapshot.selectionReason().contains("回退 FULL"));
    }

    private Run completedRun(String strategy, String masterySummary) {
        Run run = new Run("tenant-a", "student-1", "函数学习", "求定义域", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(configuration(strategy, masterySummary));
        run.start();
        run.succeed("完成");
        return run;
    }

    private AssessmentAttempt attempt(Run run, boolean correct, double before, double after) {
        return new AssessmentAttempt("tenant-a", "student-1", run.getId(), "step-1",
                "goal-1", "profile-1", "函数", correct, after, before, after,
                AssessmentAttemptType.FORMATIVE, null, "MODEL_TOOL", "学生作答", "反馈");
    }

    private EducationRunConfiguration configuration(String strategy, String masterySummary) {
        return new EducationRunConfiguration(true, "profile-1", "goal-1", null, null, null, null, null,
                "掌握函数", 0.10, 0.80, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", masterySummary, null, null, null, strategy);
    }
}
