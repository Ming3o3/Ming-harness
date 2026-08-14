package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.education.api.EducationEvidenceImpactSummaryView;
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

class EducationEvidenceImpactServiceTests {

    @Test
    void shouldFractionallyAttributeFormativeGainAcrossFrozenCitations() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run run = runWithEvidence("FULL");
        Step step = run.getSteps().get(0);
        String retrievalEvidence = EducationRetrievalEvidence.snapshot(run);
        AssessmentAttempt attempt = new AssessmentAttempt("tenant-a", "student-1", run.getId(),
                step.getId(), "goal-1", "profile-1", "函数", true,
                0.80, 0.20, 0.70, AssessmentAttemptType.FORMATIVE,
                null, "MODEL_TOOL", "学生说明了定义域限制", "正确", null,
                retrievalEvidence, null);
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(List.of(run));
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(List.of(attempt));

        EducationEvidenceImpactSummaryView summary = new EducationEvidenceImpactService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        assertEquals(1, summary.totalFormativeAssessmentCount());
        assertEquals(1, summary.assessmentsWithEvidence());
        assertEquals(1, summary.assessmentsWithMatchedEvidence());
        assertEquals(2, summary.evidenceReferenceCount());
        assertEquals(2, summary.matchedEvidenceReferenceCount());
        assertEquals(1.0, summary.evidenceLinkRate(), 0.0001);
        assertEquals(1.0, summary.snapshotMatchRate(), 0.0001);
        assertEquals(2, summary.impacts().size());
        assertEquals(0.5, summary.impacts().get(0).attributedAssessmentWeight(), 0.0001);
        assertEquals(0.25, summary.impacts().get(0).attributedMasteryGain(), 0.0001);
        assertEquals(0.50, summary.impacts().get(0).averageMasteryGain(), 0.0001);
        assertEquals(1.0, summary.impacts().get(0).attributedCorrectRate(), 0.0001);
        assertEquals("INSUFFICIENT_SAMPLE", summary.impacts().get(0).sampleStatus());

        String csv = new EducationEvidenceImpactService(runs, assessments)
                .exportCsv("tenant-a", "student-1", false);
        assertTrue(csv.startsWith("retrieval_strategy,document_id,title,citation,"));
        assertTrue(csv.contains("document:doc-1"));
    }

    @Test
    void shouldIgnoreReviewAttemptsAndRunsWithoutFrozenEvidence() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run run = runWithEvidence("FULL");
        Step step = run.getSteps().get(0);
        AssessmentAttempt review = new AssessmentAttempt("tenant-a", "student-1", run.getId(),
                step.getId(), "goal-1", "profile-1", "函数", true,
                0.80, 0.70, 0.75, AssessmentAttemptType.REVIEW,
                "review-1", "MODEL_TOOL", "复习作答", "正确", null, "[]", null);
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(List.of(run));
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(List.of(review));

        EducationEvidenceImpactSummaryView summary = new EducationEvidenceImpactService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        assertEquals(0, summary.totalFormativeAssessmentCount());
        assertEquals("NO_DATA", summary.sampleStatus());
        assertTrue(summary.impacts().isEmpty());
    }

    @Test
    void shouldRetainLegacyCitationButExposeSnapshotMismatch() {
        RunRepository runs = mock(RunRepository.class);
        AssessmentAttemptRepository assessments = mock(AssessmentAttemptRepository.class);
        Run run = runWithEvidence("FULL");
        Step step = run.getSteps().get(0);
        AssessmentAttempt attempt = new AssessmentAttempt("tenant-a", "student-1", run.getId(),
                step.getId(), "goal-1", "profile-1", "函数", false,
                0.30, 0.20, 0.23, AssessmentAttemptType.FORMATIVE,
                null, "MODEL_TOOL", "学生仍然卡住", "待补强", null,
                "[{\"documentId\":\"legacy-doc\",\"title\":\"旧教材\",\"citation\":\"document:legacy-doc\"}]",
                null);
        when(runs.findByTenantIdAndUserIdAndEducationModeTrueOrderByCreatedAtAsc(
                "tenant-a", "student-1")).thenReturn(List.of(run));
        when(assessments.findByTenantIdAndUserIdOrderByCreatedAtAsc("tenant-a", "student-1"))
                .thenReturn(List.of(attempt));

        EducationEvidenceImpactSummaryView summary = new EducationEvidenceImpactService(runs, assessments)
                .summarize("tenant-a", "student-1", false);

        assertEquals(1, summary.impacts().size());
        assertEquals(0.0, summary.snapshotMatchRate(), 0.0001);
        assertEquals(0.0, summary.impacts().get(0).snapshotMatchRate(), 0.0001);
        assertEquals(0.0, summary.impacts().get(0).averageRankingScore(), 0.0001);
    }

    private Run runWithEvidence(String strategy) {
        Run run = new Run("tenant-a", "student-1", "函数学习", "求定义域", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(new EducationRunConfiguration(
                true, "profile-1", "goal-1", null, null, null, null, null,
                "掌握函数", 0.2, 0.8, "数学", "高中一年级", "人教A版", "函数",
                null, null, "PRACTICE", "函数=0.20", null, null, "", strategy));
        run.start();
        Step step = new Step(1, StepType.MODEL, "model.complete", "求定义域");
        step.start();
        step.setContextEvidenceJson(ContextEvidenceSnapshotCodec.encode(List.of(
                new ContextEvidence("doc-1", "定义域基础", "document:doc-1#window:0",
                        "定义域基础内容", 0.9, "目标锚点", List.of(),
                        new EducationRankingBreakdown(0.9, 1.0, 0.2, 0.1,
                                0.9, 0.8, 0.0, 0.82)),
                new ContextEvidence("doc-2", "定义域例题", "document:doc-2#window:0",
                        "定义域例题内容", 0.8, "难度适配", List.of("集合"),
                        new EducationRankingBreakdown(0.8, 0.0, 0.8, 0.7,
                                0.8, 0.6, 0.0, 0.70))
        )));
        step.succeed("回答");
        run.addStep(step);
        run.succeed("回答");
        return run;
    }
}
