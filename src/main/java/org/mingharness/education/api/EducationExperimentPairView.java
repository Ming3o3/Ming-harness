package org.mingharness.education.api;

/**
 * 同一学习者-学习目标内的检索策略配对结果。
 *
 * <p>所有差值均按“对比策略 - 参考策略”计算；掌握度增益和达标率为正表示对比策略
 * 更好，达到目标所需轮次为负表示对比策略更快。只有同一学习者和同一目标同时使用
 * 两种策略且都有形成性测评时，才会进入配对样本。</p>
 */
public record EducationExperimentPairView(
        String referenceStrategy,
        String comparedStrategy,
        long pairedLearnerGoalCount,
        double referenceAverageMasteryGain,
        double comparedAverageMasteryGain,
        double masteryGainDelta,
        double referenceTargetReachRate,
        double comparedTargetReachRate,
        double targetReachRateDelta,
        double referenceAverageRoundsToTarget,
        double comparedAverageRoundsToTarget,
        double averageRoundsToTargetDelta,
        double referencePrerequisiteGapCoverage,
        double comparedPrerequisiteGapCoverage,
        double prerequisiteGapCoverageDelta,
        String sampleStatus
) {
}
