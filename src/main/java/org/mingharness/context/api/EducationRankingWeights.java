package org.mingharness.context.api;

/**
 * 一次教育证据排序使用的状态条件化权重快照。
 *
 * <p>低掌握度时提高前置缺口和知识图覆盖的权重，高掌握度时提高目标相关性、
 * 难度适配和语义相关性的权重。权重随证据快照保存，使排序能够回放和做消融。</p>
 */
public record EducationRankingWeights(
        double retrievalRelevance,
        double targetConceptMatch,
        double prerequisiteGap,
        double graphCoverage,
        double difficultyFit,
        String conditioning
) {

    public EducationRankingWeights {
        retrievalRelevance = bounded(retrievalRelevance);
        targetConceptMatch = bounded(targetConceptMatch);
        prerequisiteGap = bounded(prerequisiteGap);
        graphCoverage = bounded(graphCoverage);
        difficultyFit = bounded(difficultyFit);
        conditioning = conditioning == null || conditioning.isBlank()
                ? "STATIC" : conditioning.trim();
        double total = retrievalRelevance + targetConceptMatch + prerequisiteGap
                + graphCoverage + difficultyFit;
        if (total <= 0.0) {
            retrievalRelevance = 0.35;
            targetConceptMatch = 0.20;
            prerequisiteGap = 0.15;
            graphCoverage = 0.15;
            difficultyFit = 0.15;
        } else {
            retrievalRelevance /= total;
            targetConceptMatch /= total;
            prerequisiteGap /= total;
            graphCoverage /= total;
            difficultyFit /= total;
        }
    }

    /** 与旧版固定公式等价的权重，供历史快照和非教育上下文兼容。 */
    public static EducationRankingWeights fixed() {
        return new EducationRankingWeights(0.35, 0.20, 0.15, 0.15, 0.15, "STATIC");
    }

    /**
     * 根据目标掌握度和依赖图缺口严重度生成排序条件。
     *
     * <p>缺口越严重，越优先选择能够补齐前置知识的资料；目标掌握度越高，越优先
     * 选择目标概念、适配难度和语义相关的资料。图为空时不会伪造图覆盖权重。</p>
     */
    public static EducationRankingWeights conditioned(double targetMastery,
                                                      double prerequisiteDeficit,
                                                      boolean graphAvailable) {
        double mastery = bounded(targetMastery);
        double deficit = bounded(prerequisiteDeficit);
        double retrieval = 0.28 + 0.12 * mastery;
        double target = 0.12 + 0.20 * mastery;
        double prerequisite = 0.12 + 0.22 * deficit;
        double graph = graphAvailable ? 0.08 + 0.22 * deficit : 0.0;
        double difficulty = 0.10 + 0.10 * mastery;
        String conditioning = mastery < 0.35 ? "LOW_MASTERY_GAP_FIRST"
                : mastery >= 0.70 ? "HIGH_MASTERY_TARGET_FIRST" : "MID_MASTERY_BALANCED";
        return new EducationRankingWeights(retrieval, target, prerequisite, graph,
                difficulty, conditioning);
    }

    public double score(double retrieval, double target, double prerequisite,
                        double graph, double difficulty) {
        return retrievalRelevance * bounded(retrieval)
                + targetConceptMatch * bounded(target)
                + prerequisiteGap * bounded(prerequisite)
                + graphCoverage * bounded(graph)
                + difficultyFit * bounded(difficulty);
    }

    private static double bounded(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
