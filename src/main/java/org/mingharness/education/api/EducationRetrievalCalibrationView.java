package org.mingharness.education.api;

/** 当前租户教育检索校准快照的只读展示。 */
public record EducationRetrievalCalibrationView(
        String version,
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
        String conditioning,
        String sampleStatus
) {

    public static EducationRetrievalCalibrationView from(
            org.mingharness.education.EducationRetrievalCalibrationSnapshot snapshot) {
        var weights = snapshot.weights();
        return new EducationRetrievalCalibrationView(
                snapshot.version(), snapshot.sampleCount(), snapshot.targetGroundingMean(),
                snapshot.prerequisiteUtilityMean(), snapshot.difficultyFitMean(),
                snapshot.overallUtilityMean(), weights.retrievalRelevance(),
                weights.targetConceptMatch(), weights.prerequisiteGap(), weights.graphCoverage(),
                weights.difficultyFit(), weights.conditioning(),
                snapshot.hasEvidence() ? "CALIBRATED" : "PRIOR_ONLY");
    }
}
