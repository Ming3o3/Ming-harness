package org.mingharness.education;

import java.util.List;

/** 形成性测评的结构化观察，旧接口会自动转换成单知识点观察。 */
public record AssessmentObservation(
        boolean correct,
        double observedMastery,
        int difficultyLevel,
        List<KnowledgePointAssessment> knowledgePoints,
        boolean hintUsed,
        boolean independent,
        String questionType
) {

    public AssessmentObservation {
        observedMastery = clamp(observedMastery);
        difficultyLevel = Math.max(1, Math.min(5, difficultyLevel));
        knowledgePoints = knowledgePoints == null ? List.of() : List.copyOf(knowledgePoints);
        questionType = questionType == null || questionType.isBlank() ? null : questionType.trim();
    }

    public static AssessmentObservation legacy(boolean correct, double observedMastery) {
        return new AssessmentObservation(correct, observedMastery, 3, List.of(), false, true, null);
    }

    public static AssessmentObservation structured(boolean correct, int difficultyLevel,
                                                   List<KnowledgePointAssessment> knowledgePoints,
                                                   boolean hintUsed, boolean independent,
                                                   String questionType) {
        double aggregate = weightedScore(knowledgePoints, correct ? 1.0 : 0.0);
        return new AssessmentObservation(correct, aggregate, difficultyLevel, knowledgePoints,
                hintUsed, independent, questionType);
    }

    public boolean hasStructuredKnowledgePoints() {
        return !knowledgePoints.isEmpty();
    }

    public double effectiveEvidenceWeight() {
        double weight = independent ? 1.0 : 0.75;
        if (hintUsed) weight *= 0.65;
        return Math.max(0.25, Math.min(1.0, weight));
    }

    public double aggregateObservedMastery() {
        return weightedScore(knowledgePoints, observedMastery);
    }

    private static double weightedScore(List<KnowledgePointAssessment> points, double fallback) {
        if (points == null || points.isEmpty()) return clamp(fallback);
        double totalWeight = 0.0;
        double totalScore = 0.0;
        for (KnowledgePointAssessment point : points) {
            if (point == null) continue;
            totalWeight += point.weight();
            totalScore += point.score() * point.weight();
        }
        return totalWeight <= 0.0 ? clamp(fallback) : clamp(totalScore / totalWeight);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
