package org.mingharness.tool;

import org.junit.jupiter.api.Test;
import org.mingharness.education.EducationLearnerService;
import org.mingharness.education.LearnerMastery;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationAssessmentToolTests {

    @Test
    void shouldRecordAssessmentOnlyWithFrozenProfileAndReturnStructuredResult() {
        EducationLearnerService learnerService = mock(EducationLearnerService.class);
        when(learnerService.updateMastery(any(), any(), any(), any())).thenReturn(
                new LearnerMastery("tenant-a", "profile-1", "函数", 0.72, 4, 3));
        EducationAssessmentTool tool = new EducationAssessmentTool(learnerService, new ObjectMapper());

        String output = tool.execute("{\"conceptKey\":\"函数\",\"correct\":true,\"observedMastery\":0.9}",
                new ToolExecutionContext("run-1", "step-1", "tenant-a", "student-1", null,
                        "idempotency", "profile-1"));

        assertTrue(output.contains("\"ok\":true"));
        assertTrue(output.contains("\"conceptKey\":\"函数\""));
        assertTrue(output.contains("0.72"));
    }
}
