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

    /**
     * 从教师证据量规的租户级均值生成校准权重。
     *
     * <p>教师量规并不直接给出“检索相关性权重”，因此总体效用用于语义相关性，
     * 目标 grounding 用于目标匹配，前置补强同时影响前置缺口和图覆盖，难度适配
     * 用于难度项。以固定权重为先验，并按样本量做收缩，避免少量标注让新 Run 发生
     * 剧烈漂移。</p>
     */
    public static EducationRankingWeights calibrated(double targetGroundingMean,
                                                      double prerequisiteUtilityMean,
                                                      double difficultyFitMean,
                                                      double overallUtilityMean,
                                                      long sampleCount) {
        EducationRankingWeights prior = fixed();
        double confidence = sampleCount <= 0 ? 0.0 : sampleCount / (sampleCount + 20.0);
        double retrievalMultiplier = shrinkMultiplier(overallUtilityMean, confidence);
        double targetMultiplier = shrinkMultiplier(targetGroundingMean, confidence);
        double prerequisiteMultiplier = shrinkMultiplier(prerequisiteUtilityMean, confidence);
        double difficultyMultiplier = shrinkMultiplier(difficultyFitMean, confidence);
        return new EducationRankingWeights(
                prior.retrievalRelevance() * retrievalMultiplier,
                prior.targetConceptMatch() * targetMultiplier,
                prior.prerequisiteGap() * prerequisiteMultiplier,
                prior.graphCoverage() * prerequisiteMultiplier,
                prior.difficultyFit() * difficultyMultiplier,
                "CALIBRATED_V1:n=" + Math.max(0, sampleCount));
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

    private static double shrinkMultiplier(double score, double confidence) {
        double quality = bounded((score - 1.0) / 4.0);
        // 教师评分 3 视为中性；5 最多把先验项放大 25%，1 最多缩小 25%。
        double observedMultiplier = 0.75 + quality * 0.50;
        return 1.0 + confidence * (observedMultiplier - 1.0);
    }
}
