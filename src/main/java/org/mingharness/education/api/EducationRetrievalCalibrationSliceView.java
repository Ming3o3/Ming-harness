package org.mingharness.education.api;

/** 面向控制台展示的单个学习状态分层校准结果。 */
public record EducationRetrievalCalibrationSliceView(
        String conditioning,
        long sampleCount,
        double targetGroundingMean,
        double prerequisiteUtilityMean,
        double difficultyFitMean,
        double overallUtilityMean,
        double retrievalRelevanceWeight,
        double targetConceptMatchWeight,
        double prerequisiteGapWeight,
        double graphCoverageWeight,
        double difficultyFitWeight,
        String weightConditioning
) {

    public static EducationRetrievalCalibrationSliceView from(
            org.mingharness.education.EducationRetrievalCalibrationSlice slice) {
        var weights = slice.weights();
        return new EducationRetrievalCalibrationSliceView(slice.conditioning(), slice.sampleCount(),
                slice.targetGroundingMean(), slice.prerequisiteUtilityMean(),
                slice.difficultyFitMean(), slice.overallUtilityMean(),
                weights.retrievalRelevance(), weights.targetConceptMatch(),
                weights.prerequisiteGap(), weights.graphCoverage(), weights.difficultyFit(),
                weights.conditioning());
    }
}
