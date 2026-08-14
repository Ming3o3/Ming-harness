package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;

/**
 * 教师证据标注聚合出的、可绑定到教育 Run 的检索权重快照。
 *
 * <p>快照同时保存量规均值和样本量，便于回放时判断权重来自多少人工事实；新一轮
 * 标注不会改写已经运行过的 Run。</p>
 */
public record EducationRetrievalCalibrationSnapshot(
        String version,
        long sampleCount,
        double targetGroundingMean,
        double prerequisiteUtilityMean,
        double difficultyFitMean,
        double overallUtilityMean,
        EducationRankingWeights weights
) {

    public static final String VERSION = "retrieval-calibration-v1";

    public EducationRetrievalCalibrationSnapshot {
        version = version == null || version.isBlank() ? VERSION : version.trim();
        sampleCount = Math.max(0, sampleCount);
        targetGroundingMean = score(targetGroundingMean);
        prerequisiteUtilityMean = score(prerequisiteUtilityMean);
        difficultyFitMean = score(difficultyFitMean);
        overallUtilityMean = score(overallUtilityMean);
        weights = weights == null ? EducationRankingWeights.fixed() : weights;
    }

    public static EducationRetrievalCalibrationSnapshot prior() {
        return new EducationRetrievalCalibrationSnapshot(
                VERSION, 0, 3.0, 3.0, 3.0, 3.0,
                new EducationRankingWeights(0.35, 0.20, 0.15, 0.15, 0.15,
                        "CALIBRATED_PRIOR"));
    }

    public boolean hasEvidence() {
        return sampleCount > 0;
    }

    private static double score(double value) {
        return Double.isFinite(value) ? Math.max(1.0, Math.min(5.0, value)) : 3.0;
    }
}
