package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.Instant;

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
                0.8, 4, 3, Instant.parse("2026-01-01T00:00:00Z"));

        Instant capturedAt = Instant.parse("2026-01-15T00:00:00Z");
        String encoded = LearnerStateSnapshotCodec.encode(List.of(mastery), capturedAt);
        LearnerStateSnapshot snapshot = LearnerStateSnapshotCodec.decodeSnapshot(encoded);
        Map<String, LearnerStateEvidence> decoded = snapshot.evidence();

        LearnerStateEvidence evidence = decoded.get("函数");
        assertEquals(0.8, evidence.masteryScore(), 0.000001);
        assertEquals(4, evidence.attempts());
        assertEquals(3, evidence.correctAttempts());
        assertEquals(capturedAt, snapshot.capturedAt());
        assertTrue(evidence.retentionScore() < 1.0);
        assertTrue(evidence.effectiveMastery() < evidence.masteryScore());
    }

    @Test
    void legacyArraySnapshotRemainsFullyReadable() {
        Map<String, LearnerStateEvidence> decoded = LearnerStateSnapshotCodec.decode(
                "[{\"conceptKey\":\"函数\",\"masteryScore\":0.8,"
                        + "\"attempts\":4,\"correctAttempts\":3}]");

        LearnerStateEvidence evidence = decoded.get("函数");
        assertEquals(0.8, evidence.masteryScore(), 0.000001);
        assertEquals(1.0, evidence.retentionScore(), 0.000001);
    }

    @Test
    void retentionMovesMasteryTowardNeutralPriorWithoutChangingRawEvidence() {
        LearnerStateEvidence evidence = new LearnerStateEvidence(0.9, 10, 9,
                Instant.parse("2026-01-01T00:00:00Z"), 0.25);

        assertEquals(0.9, evidence.masteryScore(), 0.000001);
        assertEquals(0.6, evidence.effectiveMastery(), 0.000001);
        assertEquals(0.75, evidence.forgettingRisk(), 0.000001);
    }
}
