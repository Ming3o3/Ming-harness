package org.mingharness.tool;

import org.junit.jupiter.api.Test;
import org.mingharness.education.AssessmentAttempt;
import org.mingharness.education.AssessmentObservation;
import org.mingharness.education.EducationAssessmentService;
import org.mingharness.common.BusinessException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationAssessmentToolTests {

    @Test
    void shouldRecordEvidenceBackedAssessmentOnlyWithFrozenProfileAndReturnStructuredResult() {
        EducationAssessmentService assessmentService = mock(EducationAssessmentService.class);
        when(assessmentService.record(any(), any(), any(), any(), any(), any(), anyBoolean(), anyDouble(),
                any(), any(), any(), any())).thenReturn(
                new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1", "goal-1",
                        "profile-1", "函数", true, 0.9, 0.4, 0.72, "继续练习"));
        EducationAssessmentTool tool = new EducationAssessmentTool(assessmentService, new ObjectMapper());

        String output = tool.execute("{\"conceptKey\":\"函数\",\"correct\":true,\"observedMastery\":0.9,"
                        + "\"evidenceText\":\"学生说明了自变量取值范围\","
                        + "\"learnerEvidenceQuote\":\"函数的自变量不能为零\"}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));

        assertTrue(output.contains("\"ok\":true"));
        assertTrue(output.contains("\"conceptKey\":\"函数\""));
        assertTrue(output.contains("0.72"));
        assertTrue(output.contains("attemptId"));

        String missingQuote = tool.execute(
                "{\"conceptKey\":\"函数\",\"correct\":true}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));
        assertTrue(missingQuote.contains("ASSESSMENT_EVIDENCE_REQUIRED"));

        String missingQuoteWithEvidence = tool.execute(
                "{\"conceptKey\":\"函数\",\"correct\":true,\"evidenceText\":\"学生写出推理\"}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));
        assertTrue(missingQuoteWithEvidence.contains("ASSESSMENT_LEARNER_EVIDENCE_QUOTE_REQUIRED"));
    }

    @Test
    void shouldReturnRecoverableResultWhenLearnerQuoteDoesNotMatchCurrentInput() {
        EducationAssessmentService assessmentService = mock(EducationAssessmentService.class);
        when(assessmentService.record(any(), any(), any(), any(), any(), any(), anyBoolean(), anyDouble(),
                any(), any(), any(), any())).thenThrow(new BusinessException(
                org.springframework.http.HttpStatus.CONFLICT,
                "ASSESSMENT_LEARNER_EVIDENCE_QUOTE_MISMATCH",
                "模型评价引用的学习者原话不属于本轮输入，不能更新掌握度"));
        EducationAssessmentTool tool = new EducationAssessmentTool(assessmentService, new ObjectMapper());

        String output = tool.execute("{\"conceptKey\":\"反比例函数\",\"correct\":true,"
                        + "\"evidenceText\":\"模型观察到作答过程\","
                        + "\"learnerEvidenceQuote\":\"不存在于本轮输入的引用\"}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));

        assertTrue(output.contains("\"ok\":false"));
        assertTrue(output.contains("\"recoverable\":true"));
        assertTrue(output.contains("ASSESSMENT_LEARNER_EVIDENCE_QUOTE_MISMATCH"));
    }

    @Test
    void shouldReturnRecoverableResultWhenLearnerQuoteIsMissingInsteadOfFailingSchemaValidation() {
        EducationAssessmentService assessmentService = mock(EducationAssessmentService.class);
        EducationAssessmentTool tool = new EducationAssessmentTool(assessmentService, new ObjectMapper());

        String output = tool.execute("{\"conceptKey\":\"反比例函数\",\"correct\":true,"
                        + "\"evidenceText\":\"模型观察到作答过程\"}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));

        assertTrue(output.contains("\"ok\":false"));
        assertTrue(output.contains("\"recoverable\":true"));
        assertTrue(output.contains("ASSESSMENT_LEARNER_EVIDENCE_QUOTE_REQUIRED"));
    }

    @Test
    void shouldParseStructuredKnowledgePointEvidenceAndDifficulty() {
        EducationAssessmentService assessmentService = mock(EducationAssessmentService.class);
        when(assessmentService.record(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.any(AssessmentObservation.class),
                any(), any(), any(), any())).thenAnswer(invocation -> {
            AssessmentObservation observation = invocation.getArgument(6);
            assertTrue(observation.hasStructuredKnowledgePoints());
            assertTrue(observation.difficultyLevel() == 4);
            AssessmentAttempt attempt = new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1", "goal-1",
                    "profile-1", "二次函数", true, observation.aggregateObservedMastery(), 0.2, 0.45,
                    "结构化评价");
            attempt.setStructuredEvidence(observation.difficultyLevel(), "[{\"conceptKey\":\"概念\"}]",
                    observation.hintUsed(), observation.independent(), observation.questionType());
            return attempt;
        });
        EducationAssessmentTool tool = new EducationAssessmentTool(assessmentService, new ObjectMapper());

        String output = tool.execute("{\"conceptKey\":\"二次函数\",\"correct\":true,"
                        + "\"difficultyLevel\":4,\"questionType\":\"开放题\","
                        + "\"knowledgePoints\":[{\"conceptKey\":\"概念\",\"correct\":true,\"score\":1,\"weight\":0.4},"
                        + "{\"conceptKey\":\"一般形式\",\"correct\":false,\"score\":0,\"weight\":0.6}],"
                        + "\"evidenceText\":\"学生写出定义\",\"learnerEvidenceQuote\":\"我的答案\"}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));

        assertTrue(output.contains("\"difficultyLevel\":4"));
        assertTrue(output.contains("knowledgePointScores"));
    }
}
