package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.common.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void shouldBalanceByAllocationCountEvenWhenSomeRunsHaveNoAssessmentOutcome() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        EducationRetrievalCalibrationService calibration = mock(EducationRetrievalCalibrationService.class);
        when(calibration.conditioningFor(any(EducationRunConfiguration.class)))
                .thenReturn("LOW_MASTERY_GAP_FIRST");
        when(calibration.conditioningFor(any(Run.class))).thenReturn("LOW_MASTERY_GAP_FIRST");

        List<Run> historicalRuns = new ArrayList<>();
        historicalRuns.add(completedRun("FULL", "函数=0.10"));
        historicalRuns.add(completedRun("CALIBRATED", "函数=0.10"));
        historicalRuns.add(completedRun("CALIBRATED", "函数=0.10"));
        historicalRuns.add(completedRun("NO_DEPENDENCY_GRAPH", "函数=0.10"));
        historicalRuns.add(completedRun("NO_DEPENDENCY_GRAPH", "函数=0.10"));
        historicalRuns.add(completedRun("STATIC_WEIGHT", "函数=0.10"));
        historicalRuns.add(completedRun("STATIC_WEIGHT", "函数=0.10"));
        when(runs.findByTenantIdAndEducationModeTrueOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(historicalRuns);
        when(assessments.findByTenantIdOrderByCreatedAtAsc("tenant-a"))
                .thenReturn(List.of());

        EducationRetrievalPolicySnapshot snapshot = new EducationRetrievalPolicyService(
                runs, assessments, calibration).snapshotFor("tenant-a", "student-1",
                configuration("BALANCED_EXPERIMENT", "函数=0.10"));

        assertEquals("FULL", snapshot.selectedStrategy());
        assertEquals(7, snapshot.allocationRunCount());
        var full = snapshot.candidates().stream().filter(item -> item.strategy().equals("FULL"))
                .findFirst().orElseThrow();
        assertEquals(1, full.allocationCount());
        assertEquals(0, full.runCount());
        assertTrue(snapshot.selectionReason().contains("均衡"));
    }

    @Test
    void shouldReplayCalibrationSnapshotForAdaptiveSelectionOfCalibratedMethod() {
        EducationRetrievalPolicySnapshot policy = new EducationRetrievalPolicySnapshot(
                EducationRetrievalPolicySnapshot.VERSION, "LOW_MASTERY_GAP_FIRST", "CALIBRATED", 3,
                "均衡", List.of(), 3, "{\"version\":\"retrieval-calibration-v2\"}");
        EducationRetrievalPolicySnapshot decoded = EducationRetrievalPolicySnapshotCodec.decode(
                EducationRetrievalPolicySnapshotCodec.encode(policy));

        assertEquals("CALIBRATED", decoded.selectedStrategy());
        assertEquals("{\"version\":\"retrieval-calibration-v2\"}", decoded.calibrationSnapshot());
    }

    @Test
    void shouldExposeFrozenRunPolicyOnlyToOwnerOrEvaluator() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        EducationRetrievalCalibrationService calibration = mock(EducationRetrievalCalibrationService.class);
        Run run = completedRun("ADAPTIVE", "函数=0.10");
        String encoded = EducationRetrievalPolicySnapshotCodec.encode(new EducationRetrievalPolicySnapshot(
                EducationRetrievalPolicySnapshot.VERSION, "LOW_MASTERY_GAP_FIRST", "CALIBRATED", 10,
                "按状态条件化学习结果选择收缩分数最高的 CALIBRATED",
                List.of(new EducationRetrievalPolicyCandidate("CALIBRATED", 5, 5, 0.6,
                        0.8, 0.8, 0.75, 0.5, 0.625, "ANALYSIS_READY"))));
        run.attachEducationRetrievalPolicy(encoded);
        when(runs.findById(run.getId())).thenReturn(java.util.Optional.of(run));
        when(calibration.conditioningFor(run)).thenReturn("LOW_MASTERY_GAP_FIRST");

        EducationRetrievalPolicyService service = new EducationRetrievalPolicyService(
                runs, assessments, calibration);
        var view = service.viewForRun("tenant-a", "student-1", run.getId(), false);

        assertEquals(run.getId(), view.runId());
        assertEquals("ADAPTIVE", view.requestedStrategy());
        assertEquals("CALIBRATED", view.effectiveStrategy());
        assertTrue(view.snapshotFrozen());
        assertEquals("ADAPTIVE_POLICY", view.snapshotType());
        assertEquals(1, view.candidates().size());
        assertThrows(BusinessException.class,
                () -> service.viewForRun("tenant-a", "another-student", run.getId(), false));
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
