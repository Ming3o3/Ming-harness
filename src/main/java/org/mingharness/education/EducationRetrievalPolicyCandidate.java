package org.mingharness.education;

/** 一个候选检索策略在当前学习状态下的历史结果统计。 */
public record EducationRetrievalPolicyCandidate(
        String strategy,
        long runCount,
        long assessmentCount,
        double masteryGainMean,
        double accuracyRate,
        double targetReachRate,
        double outcomeScore,
        double confidence,
        double adjustedScore,
        String sampleStatus,
        long allocationCount
) {

    /** 兼容旧版快照和测试调用；旧版 runCount 同时代表已分配样本数。 */
    public EducationRetrievalPolicyCandidate(String strategy, long runCount, long assessmentCount,
                                              double masteryGainMean, double accuracyRate,
                                              double targetReachRate, double outcomeScore,
                                              double confidence, double adjustedScore,
                                              String sampleStatus) {
        this(strategy, runCount, assessmentCount, masteryGainMean, accuracyRate,
                targetReachRate, outcomeScore, confidence, adjustedScore, sampleStatus, runCount);
    }

    public EducationRetrievalPolicyCandidate {
        strategy = strategy == null || strategy.isBlank() ? EducationRetrievalStrategy.FULL.name()
                : strategy.trim().toUpperCase(java.util.Locale.ROOT);
        runCount = Math.max(0, runCount);
        assessmentCount = Math.max(0, assessmentCount);
        masteryGainMean = bounded(masteryGainMean, -1.0, 1.0);
        accuracyRate = bounded(accuracyRate, 0.0, 1.0);
        targetReachRate = bounded(targetReachRate, 0.0, 1.0);
        outcomeScore = bounded(outcomeScore, 0.0, 1.0);
        confidence = bounded(confidence, 0.0, 1.0);
        adjustedScore = bounded(adjustedScore, 0.0, 1.0);
        sampleStatus = sampleStatus == null || sampleStatus.isBlank()
                ? runCount == 0 ? "NO_DATA" : "INSUFFICIENT_SAMPLE" : sampleStatus.trim();
        allocationCount = Math.max(0, allocationCount);
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : 0.0;
    }
}
