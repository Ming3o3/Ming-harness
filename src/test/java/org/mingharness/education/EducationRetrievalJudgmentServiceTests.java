package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.education.api.EducationRetrievalJudgmentRequest;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.runtime.repository.RunRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationRetrievalJudgmentServiceTests {

    @Test
    void shouldAcceptOnlyEvidenceFromSucceededEducationRunSnapshot() {
        RunRepository runs = mock(RunRepository.class);
        EducationRetrievalJudgmentRepository judgments = mock(EducationRetrievalJudgmentRepository.class);
        Run run = educationRun();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(judgments.save(any(EducationRetrievalJudgment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EducationRetrievalJudgmentViewAssertions result = new EducationRetrievalJudgmentViewAssertions(
                new EducationRetrievalJudgmentService(runs, judgments, new SensitiveDataSanitizer())
                        .submit("tenant-a", "teacher-1", run.getId(),
                                new EducationRetrievalJudgmentRequest(
                                        run.getSteps().get(0).getId(),
                                        "document:doc-1#window:0", 5, 4, 3, 5,
                                        "目标清晰，前置解释可用")));

        assertEquals("document:doc-1#window:0", result.citation());
        assertEquals("doc-1", result.documentId());
        assertEquals(5, result.overallScore());
        assertEquals("retrieval-v1", result.rubricVersion());
    }

    @Test
    void shouldRejectCitationThatWasNotInTheRunSnapshot() {
        RunRepository runs = mock(RunRepository.class);
        EducationRetrievalJudgmentRepository judgments = mock(EducationRetrievalJudgmentRepository.class);
        Run run = educationRun();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        EducationRetrievalJudgmentService service = new EducationRetrievalJudgmentService(
                runs, judgments, new SensitiveDataSanitizer());

        assertThrows(org.mingharness.common.BusinessException.class, () -> service.submit(
                "tenant-a", "teacher-1", run.getId(), new EducationRetrievalJudgmentRequest(
                        null, "document:unknown", 1, 1, 1, 1, null)));
    }

    @Test
    void shouldRejectNonEducationOrUnfinishedRun() {
        RunRepository runs = mock(RunRepository.class);
        EducationRetrievalJudgmentRepository judgments = mock(EducationRetrievalJudgmentRepository.class);
        Run run = new Run("tenant-a", "student-1", "普通 Run", "问题", BigDecimal.ONE,
                "model", "prompt", "policy");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        EducationRetrievalJudgmentService service = new EducationRetrievalJudgmentService(
                runs, judgments, new SensitiveDataSanitizer());

        assertThrows(org.mingharness.common.BusinessException.class, () -> service.submit(
                "tenant-a", "teacher-1", run.getId(), new EducationRetrievalJudgmentRequest(
                        null, "document:doc-1", 1, 1, 1, 1, null)));
    }

    @Test
    void shouldReturnBusinessErrorForMissingScoreWhenServiceIsCalledDirectly() {
        RunRepository runs = mock(RunRepository.class);
        EducationRetrievalJudgmentRepository judgments = mock(EducationRetrievalJudgmentRepository.class);
        Run run = educationRun();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        EducationRetrievalJudgmentService service = new EducationRetrievalJudgmentService(
                runs, judgments, new SensitiveDataSanitizer());

        org.mingharness.common.BusinessException exception = assertThrows(
                org.mingharness.common.BusinessException.class, () -> service.submit(
                        "tenant-a", "teacher-1", run.getId(), new EducationRetrievalJudgmentRequest(
                                null, "document:doc-1#window:0", null, 1, 1, 1, null)));

        assertEquals("EDUCATION_RETRIEVAL_JUDGMENT_SCORE_INVALID", exception.getCode());
    }

    private Run educationRun() {
        Run run = new Run("tenant-a", "student-1", "函数学习", "函数", BigDecimal.ONE,
                "model", "prompt", "policy");
        run.attachEducationConfiguration(new EducationRunConfiguration(
                true, "profile-1", "goal-1", "函数目标", 0.2, 0.8,
                "数学", "高中一年级", "人教A版", "函数", null, null,
                "EXPLAIN", "函数=0.20"));
        run.start();
        Step step = new Step(1, StepType.MODEL, "model.complete", "函数");
        step.start();
        step.setContextEvidenceJson(ContextEvidenceSnapshotCodec.encode(List.of(
                new ContextEvidence("doc-1", "函数课件", "document:doc-1#window:0",
                        "函数定义", 0.9, "目标证据锚点", List.of()))));
        step.succeed("回答");
        run.addStep(step);
        run.succeed("完成");
        return run;
    }

    private record EducationRetrievalJudgmentViewAssertions(
            org.mingharness.education.api.EducationRetrievalJudgmentView view) {
        private String citation() { return view.evidenceCitation(); }
        private String documentId() { return view.documentId(); }
        private int overallScore() { return view.overallUtilityScore(); }
        private String rubricVersion() { return view.rubricVersion(); }
    }
}
