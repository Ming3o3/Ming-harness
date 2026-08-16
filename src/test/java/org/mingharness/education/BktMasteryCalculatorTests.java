package org.mingharness.education;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BktMasteryCalculatorTests {

    @Test
    void correctHardIndependentAnswerRaisesMasteryMoreThanEasyGuessedAnswer() {
        double easy = BktMasteryCalculator.update(0.35, true, 1).nextMastery();
        double hard = BktMasteryCalculator.update(0.35, true, 5).nextMastery();

        assertTrue(hard > easy);
    }

    @Test
    void hintAndNonIndependentEvidenceHaveSmallerImpact() {
        double independent = BktMasteryCalculator.update(0.35, true, 3, 1.0, false).nextMastery();
        double assisted = BktMasteryCalculator.update(0.35, true, 3, 0.75, true).nextMastery();

        assertTrue(assisted < independent);
    }

    @Test
    void structuredObservationAggregatesKnowledgePointWeights() {
        AssessmentObservation observation = AssessmentObservation.structured(true, 4,
                java.util.List.of(
                        new KnowledgePointAssessment("概念", true, 1.0, 0.4),
                        new KnowledgePointAssessment("一般形式", false, 0.0, 0.6)),
                false, true, "开放题");

        assertTrue(Math.abs(observation.aggregateObservedMastery() - 0.4) < 1e-9);
        assertTrue(observation.hasStructuredKnowledgePoints());
    }
}
