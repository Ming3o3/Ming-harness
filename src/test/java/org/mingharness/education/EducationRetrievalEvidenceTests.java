package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationRetrievalEvidenceTests {

    @Test
    void shouldSnapshotDistinctRunSourcesWithoutPersistingTheirExcerpt() {
        Run run = new Run("tenant-a", "student-1", "函数作业", "学习",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        Step step = new Step(1, StepType.MODEL, "教学", "函数");
        step.setContextEvidenceJson("[{\"documentId\":\"doc-1\",\"title\":\"函数课件\","
                + "\"citation\":\"document:doc-1#chunk:0\",\"excerpt\":\"不应写入测评快照\"},"
                + "{\"documentId\":\"doc-1\",\"title\":\"函数课件\","
                + "\"citation\":\"document:doc-1#chunk:0\",\"excerpt\":\"重复\"}]");
        run.addStep(step);

        String snapshot = EducationRetrievalEvidence.snapshot(run);
        var references = EducationRetrievalEvidence.decode(snapshot);

        assertEquals(1, references.size());
        assertEquals("doc-1", references.get(0).documentId());
        assertEquals("document:doc-1#chunk:0", references.get(0).citation());
        assertTrue(!snapshot.contains("不应写入测评快照"));
    }
}
