package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationProgrammingTestCaseSnapshotCodecTests {

    @Test
    void freezesOnlyEnabledCasesAndPreservesExactInputOutput() {
        LearningAssignmentTestCase visible = new LearningAssignmentTestCase(
                "tenant-a", "assignment-a", "normal", "普通样例", "2 3\n", "5\n",
                false, 2.0, 1);
        LearningAssignmentTestCase hidden = new LearningAssignmentTestCase(
                "tenant-a", "assignment-a", "edge", "边界样例", "0 0\n", "0\n",
                true, 1.0, 2);
        hidden.disable(Instant.now());

        String encoded = EducationProgrammingTestCaseSnapshotCodec.encode(List.of(visible, hidden));
        EducationProgrammingTestCaseSnapshot decoded =
                EducationProgrammingTestCaseSnapshotCodec.decode(encoded);

        assertEquals(1, decoded.cases().size());
        assertEquals("normal", decoded.cases().get(0).caseKey());
        assertEquals("2 3\n", decoded.cases().get(0).input());
        assertEquals("5\n", decoded.cases().get(0).expectedOutput());
        assertEquals(2.0, decoded.cases().get(0).weight());
        assertTrue(decoded.version().contains("T") || decoded.version().equals("legacy"));
    }

    @Test
    void malformedOrLegacySnapshotFallsBackToEmpty() {
        assertTrue(EducationProgrammingTestCaseSnapshotCodec.decode("[]").cases().isEmpty());
        assertTrue(EducationProgrammingTestCaseSnapshotCodec.decode("not-json").cases().isEmpty());
        assertTrue(EducationProgrammingTestCaseSnapshotCodec.decode(
                "{\"version\":\"v1\",\"cases\":[{\"caseKey\":\"x\"}]}").cases().isEmpty());
    }
}
