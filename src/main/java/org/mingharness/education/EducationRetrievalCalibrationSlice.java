package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;

/** 一个学习状态分层内的教师检索标注聚合。 */
public record EducationRetrievalCalibrationSlice(
        String conditioning,
        long sampleCount,
        double targetGroundingMean,
        double prerequisiteUtilityMean,
        double difficultyFitMean,
        double overallUtilityMean,
        EducationRankingWeights weights
) {

    public EducationRetrievalCalibrationSlice {
        conditioning = conditioning == null || conditioning.isBlank()
                ? "UNKNOWN" : conditioning.trim();
        sampleCount = Math.max(0, sampleCount);
        targetGroundingMean = score(targetGroundingMean);
        prerequisiteUtilityMean = score(prerequisiteUtilityMean);
        difficultyFitMean = score(difficultyFitMean);
        overallUtilityMean = score(overallUtilityMean);
        weights = weights == null ? EducationRankingWeights.fixed() : weights;
    }

    public boolean hasEvidence() {
        return sampleCount > 0;
    }

    private static double score(double value) {
        return Double.isFinite(value) ? Math.max(1.0, Math.min(5.0, value)) : 3.0;
    }
}
