package org.mingharness.education.api;

/** 面向实验面板展示的自适应策略候选统计。 */
public record EducationRetrievalPolicyCandidateView(
        String strategy,
        long runCount,
        long assessmentCount,
        double masteryGainMean,
        double accuracyRate,
        double targetReachRate,
        double outcomeScore,
        double confidence,
        double adjustedScore,
        String sampleStatus
) {

    public static EducationRetrievalPolicyCandidateView from(
            org.mingharness.education.EducationRetrievalPolicyCandidate candidate) {
        return new EducationRetrievalPolicyCandidateView(candidate.strategy(), candidate.runCount(),
                candidate.assessmentCount(), candidate.masteryGainMean(), candidate.accuracyRate(),
                candidate.targetReachRate(), candidate.outcomeScore(), candidate.confidence(),
                candidate.adjustedScore(), candidate.sampleStatus());
    }
}
