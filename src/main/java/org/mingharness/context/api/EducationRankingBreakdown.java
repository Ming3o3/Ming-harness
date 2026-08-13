package org.mingharness.context.api;

/**
 * 教育证据的可审计排序拆解。
 *
 * <p>各分量均限制在 [0,1]。finalScore 是证据在当前上下文选择轮次中的最终效用，
 * 其中 marginalCoverageScore 和 redundancyPenalty 反映它相对于已经选中的证据的边际
 * 贡献，而不是一个脱离上下文的静态相关性分数。</p>
 */
public record EducationRankingBreakdown(
        double retrievalRelevance,
        double targetConceptMatch,
        double prerequisiteGap,
        double graphCoverage,
        double difficultyFit,
        double marginalCoverageScore,
        double redundancyPenalty,
        double finalScore,
        EducationRankingWeights weights
) {

    /** 兼容旧版八分量排序快照，使用固定权重。 */
    public EducationRankingBreakdown(double retrievalRelevance, double targetConceptMatch,
                                     double prerequisiteGap, double graphCoverage,
                                     double difficultyFit, double marginalCoverageScore,
                                     double redundancyPenalty, double finalScore) {
        this(retrievalRelevance, targetConceptMatch, prerequisiteGap, graphCoverage,
                difficultyFit, marginalCoverageScore, redundancyPenalty, finalScore,
                EducationRankingWeights.fixed());
    }

    public EducationRankingBreakdown {
        retrievalRelevance = bounded(retrievalRelevance);
        targetConceptMatch = bounded(targetConceptMatch);
        prerequisiteGap = bounded(prerequisiteGap);
        graphCoverage = bounded(graphCoverage);
        difficultyFit = bounded(difficultyFit);
        marginalCoverageScore = bounded(marginalCoverageScore);
        redundancyPenalty = bounded(redundancyPenalty);
        finalScore = Double.isFinite(finalScore) ? Math.max(0.0, Math.min(1.0, finalScore)) : 0.0;
        weights = weights == null ? EducationRankingWeights.fixed() : weights;
    }

    public static EducationRankingBreakdown empty() {
        return new EducationRankingBreakdown(0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0);
    }

    /** 固定排序分量，不含证据集合选择阶段的边际覆盖和冗余惩罚。 */
    public double baseScore() {
        return weights.score(retrievalRelevance, targetConceptMatch, prerequisiteGap,
                graphCoverage, difficultyFit);
    }

    public EducationRankingBreakdown withSelection(double marginalCoverage,
                                                   double redundancy,
                                                   double selectionScore) {
        return new EducationRankingBreakdown(retrievalRelevance, targetConceptMatch,
                prerequisiteGap, graphCoverage, difficultyFit, marginalCoverage,
                redundancy, selectionScore, weights);
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
