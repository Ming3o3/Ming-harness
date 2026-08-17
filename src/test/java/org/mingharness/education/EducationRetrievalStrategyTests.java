package org.mingharness.education;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationRetrievalStrategyTests {

    @Test
    void fixedHybridControlDisablesOnlyStateAndGraphSignals() {
        EducationRetrievalStrategy strategy = EducationRetrievalStrategy.NO_STATE_NO_GRAPH;

        assertTrue(strategy.usesVector());
        assertTrue(strategy.usesKeyword());
        assertFalse(strategy.usesLearnerState());
        assertFalse(strategy.usesDependencyGraph());
        assertFalse(strategy.usesAdaptiveWeights());
    }

    @Test
    void pointEstimateAblationKeepsStateAndGraphButDisablesUncertaintySignal() {
        EducationRetrievalStrategy strategy = EducationRetrievalStrategy.FULL_POINT_ESTIMATE;

        assertTrue(strategy.usesVector());
        assertTrue(strategy.usesKeyword());
        assertTrue(strategy.usesLearnerState());
        assertTrue(strategy.usesDependencyGraph());
        assertTrue(strategy.usesAdaptiveWeights());
        assertFalse(strategy.usesUncertaintyAwareState());
    }
}
