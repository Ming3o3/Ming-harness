package org.mingharness.education;

/** 单道题对一个知识点的结构化评价结果。 */
public record KnowledgePointAssessment(
        String conceptKey,
        boolean correct,
        double score,
        double weight,
        String evidenceText
) {

    public KnowledgePointAssessment {
        conceptKey = conceptKey == null ? "" : conceptKey.trim();
        if (conceptKey.isBlank()) throw new IllegalArgumentException("知识点不能为空");
        score = clamp(score);
        weight = weight <= 0.0 || !Double.isFinite(weight) ? 1.0 : Math.min(1.0, weight);
        evidenceText = evidenceText == null || evidenceText.isBlank() ? null : evidenceText.trim();
    }

    public KnowledgePointAssessment(String conceptKey, boolean correct, double score, double weight) {
        this(conceptKey, correct, score, weight, null);
    }

    public static KnowledgePointAssessment fromLegacy(String conceptKey, boolean correct,
                                                       double observedMastery) {
        return new KnowledgePointAssessment(conceptKey, correct, observedMastery, 1.0, null);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
