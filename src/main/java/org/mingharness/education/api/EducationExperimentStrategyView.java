package org.mingharness.education.api;

/** 按冻结检索策略聚合的可复现实验指标。 */
public record EducationExperimentStrategyView(
        String retrievalStrategy,
        long runCount,
        long successfulRunCount,
        long runsWithEvidence,
        double evidenceCoverageRate,
        double averageEvidenceCount,
        double averageUniqueEvidenceCount,
        double averageEvidenceChars,
        double averageUtilityPerThousandChars,
        double averageMarginalCoveragePerThousandChars,
        double prerequisiteGapCoverageRate,
        double evidenceRedundancyRate,
        double averageRankingScore,
        double averageMarginalCoverage,
        double averageTargetConceptMatch,
        double averageGraphCoverage,
        double averageDifficultyFit,
        long assessmentCount,
        long correctAssessmentCount,
        double assessmentAccuracyRate,
        double averageMasteryGain,
        long targetGoalCount,
        long targetReachedGoalCount,
        double targetReachRate,
        double averageRoundsToTarget,
        String sampleStatus
) {
}
