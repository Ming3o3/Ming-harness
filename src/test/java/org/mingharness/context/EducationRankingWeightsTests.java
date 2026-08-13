package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.context.api.EducationRankingWeights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationRankingWeightsTests {

    @Test
    void shouldPrioritizePrerequisiteGapWhenMasteryIsLow() {
        EducationRankingWeights weights = EducationRankingWeights.conditioned(0.10, 0.90, true);

        assertEquals("LOW_MASTERY_GAP_FIRST", weights.conditioning());
        assertTrue(weights.prerequisiteGap() + weights.graphCoverage()
                > weights.targetConceptMatch());
        assertEquals(1.0, weights.retrievalRelevance() + weights.targetConceptMatch()
                + weights.prerequisiteGap() + weights.graphCoverage() + weights.difficultyFit(),
                0.000001);
    }

    @Test
    void shouldPrioritizeTargetAndDifficultyWhenMasteryIsHigh() {
        EducationRankingWeights weights = EducationRankingWeights.conditioned(0.90, 0.10, true);

        assertEquals("HIGH_MASTERY_TARGET_FIRST", weights.conditioning());
        assertTrue(weights.targetConceptMatch() > weights.prerequisiteGap() + weights.graphCoverage());
        assertTrue(weights.difficultyFit() > EducationRankingWeights.conditioned(0.10, 0.90, true)
                .difficultyFit());
    }
}
