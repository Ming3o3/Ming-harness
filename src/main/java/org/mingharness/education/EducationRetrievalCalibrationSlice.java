package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;

/** 一个学习状态分层内的教师检索标注与形成性学习结果聚合。 */
public record EducationRetrievalCalibrationSlice(
        String conditioning,
        long sampleCount,
        double targetGroundingMean,
        double prerequisiteUtilityMean,
        double difficultyFitMean,
        double overallUtilityMean,
        EducationRankingWeights weights,
        long outcomeAssessmentCount,
        double outcomeMasteryGainMean,
        double outcomeCorrectRate,
        double outcomeTargetReachRate,
        double outcomeScore
) {

    /** 兼容只包含教师量规的旧状态分层。 */
    public EducationRetrievalCalibrationSlice(String conditioning, long sampleCount,
                                              double targetGroundingMean,
                                              double prerequisiteUtilityMean,
                                              double difficultyFitMean,
                                              double overallUtilityMean,
                                              EducationRankingWeights weights) {
        this(conditioning, sampleCount, targetGroundingMean, prerequisiteUtilityMean,
                difficultyFitMean, overallUtilityMean, weights, 0, 0.0, 0.0, 0.0, 0.0);
    }

    public EducationRetrievalCalibrationSlice {
        conditioning = conditioning == null || conditioning.isBlank()
                ? "UNKNOWN" : conditioning.trim();
        sampleCount = Math.max(0, sampleCount);
        targetGroundingMean = score(targetGroundingMean);
        prerequisiteUtilityMean = score(prerequisiteUtilityMean);
        difficultyFitMean = score(difficultyFitMean);
        overallUtilityMean = score(overallUtilityMean);
        weights = weights == null ? EducationRankingWeights.fixed() : weights;
        outcomeAssessmentCount = Math.max(0, outcomeAssessmentCount);
        outcomeMasteryGainMean = bounded(outcomeMasteryGainMean, -1.0, 1.0);
        outcomeCorrectRate = bounded(outcomeCorrectRate, 0.0, 1.0);
        outcomeTargetReachRate = bounded(outcomeTargetReachRate, 0.0, 1.0);
        outcomeScore = bounded(outcomeScore, 0.0, 1.0);
    }

    public boolean hasEvidence() {
        return sampleCount > 0;
    }

    private static double score(double value) {
        return Double.isFinite(value) ? Math.max(1.0, Math.min(5.0, value)) : 3.0;
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : 0.0;
    }
}
