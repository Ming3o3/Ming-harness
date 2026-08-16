package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnerStateEvidenceTests {

    @Test
    void uncertaintyShrinksAsEvidenceAccumulates() {
        LearnerStateEvidence sparse = new LearnerStateEvidence(0.8, 1, 1);
        LearnerStateEvidence repeated = new LearnerStateEvidence(0.8, 20, 16);

        assertTrue(repeated.uncertainty() < sparse.uncertainty());
        assertTrue(repeated.conservativeMastery() > sparse.conservativeMastery());
    }

    @Test
    void snapshotCodecKeepsEvidenceNeededForRunReplay() {
        LearnerMastery mastery = new LearnerMastery("tenant-a", "profile-a", "函数",
                0.8, 4, 3);

        Map<String, LearnerStateEvidence> decoded = LearnerStateSnapshotCodec.decode(
                LearnerStateSnapshotCodec.encode(List.of(mastery)));

        LearnerStateEvidence evidence = decoded.get("函数");
        assertEquals(0.8, evidence.masteryScore(), 0.000001);
        assertEquals(4, evidence.attempts());
        assertEquals(3, evidence.correctAttempts());
    }
}
