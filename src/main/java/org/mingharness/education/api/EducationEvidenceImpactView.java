package org.mingharness.education.api;

/**
 * 单个教育检索证据与形成性学习结果的归因聚合。
 *
 * <p>一次测评引用多个来源时，mastery gain 和正确性按引用数量做分数分摊；因此
 * attributedAssessmentWeight 可能是小数，避免把同一次测评重复计入每个来源。</p>
 */
public record EducationEvidenceImpactView(
        String retrievalStrategy,
        String documentId,
        String title,
        String citation,
        long evidenceReferenceCount,
        double attributedAssessmentWeight,
        double attributedMasteryGain,
        double averageMasteryGain,
        double attributedCorrectRate,
        double averageRankingScore,
        double averageMarginalCoverage,
        double averageTargetConceptMatch,
        double averageGraphCoverage,
        double averageDifficultyFit,
        double snapshotMatchRate,
        String sampleStatus
) {
}
