package org.mingharness.tool;

import org.junit.jupiter.api.Test;
import org.mingharness.education.AssessmentAttempt;
import org.mingharness.education.EducationAssessmentService;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                any(), any(), any())).thenReturn(
                new AssessmentAttempt("tenant-a", "student-1", "run-1", "step-1", "goal-1",
                        "profile-1", "函数", true, 0.9, 0.4, 0.72, "继续练习"));
        EducationAssessmentTool tool = new EducationAssessmentTool(assessmentService, new ObjectMapper());

        String output = tool.execute("{\"conceptKey\":\"函数\",\"correct\":true,\"observedMastery\":0.9,"
                        + "\"evidenceText\":\"学生说明了自变量取值范围\"}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));

        assertTrue(output.contains("\"ok\":true"));
        assertTrue(output.contains("\"conceptKey\":\"函数\""));
        assertTrue(output.contains("0.72"));
        assertTrue(output.contains("attemptId"));

        assertThrows(IllegalArgumentException.class, () -> tool.execute(
                "{\"conceptKey\":\"函数\",\"correct\":true}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1")));
    }
}
