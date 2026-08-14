package org.mingharness.education.api;

import java.util.List;

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
        String sampleStatus,
        List<EducationRetrievalCalibrationSliceView> stateSlices
) {

    public EducationRetrievalCalibrationView {
        stateSlices = stateSlices == null ? List.of() : List.copyOf(stateSlices);
    }

    /** 兼容没有状态分层的 v1 API 调用方。 */
    public EducationRetrievalCalibrationView(String version, long sampleCount,
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
                                             String sampleStatus) {
        this(version, sampleCount, targetGroundingMean, prerequisiteUtilityMean,
                difficultyFitMean, overallUtilityMean, retrievalRelevanceWeight,
                targetConceptMatchWeight, prerequisiteGapWeight, graphCoverageWeight,
                difficultyFitWeight, conditioning, sampleStatus, List.of());
    }

    public static EducationRetrievalCalibrationView from(
            org.mingharness.education.EducationRetrievalCalibrationSnapshot snapshot) {
        var weights = snapshot.weights();
        return new EducationRetrievalCalibrationView(
                snapshot.version(), snapshot.sampleCount(), snapshot.targetGroundingMean(),
                snapshot.prerequisiteUtilityMean(), snapshot.difficultyFitMean(),
                snapshot.overallUtilityMean(), weights.retrievalRelevance(),
                weights.targetConceptMatch(), weights.prerequisiteGap(), weights.graphCoverage(),
                weights.difficultyFit(), weights.conditioning(),
                snapshot.hasEvidence() ? "CALIBRATED" : "PRIOR_ONLY",
                snapshot.stateSlices().values().stream()
                        .map(EducationRetrievalCalibrationSliceView::from).toList());
    }
}
