package org.mingharness.education;

import java.time.Instant;

/**
 * 一次教育 Run 创建时冻结的知识点掌握度证据。
 *
 * <p>掌握度分数本身不足以说明状态是否可靠。这里同时保存观测次数和答对次数，
 * 并用 Wilson 区间估计不确定性。检索阶段使用保守下界，避免少量成功作答把
 * 学习者误判为已经掌握，从而跳过必要的前置补强。</p>
 */
public record LearnerStateEvidence(
        double masteryScore,
        int attempts,
        int correctAttempts,
        Instant lastAssessedAt,
        double retentionScore
) {

    /** 兼容尚未携带时间证据的旧调用方；旧状态按完全保持处理。 */
    public LearnerStateEvidence(double masteryScore, int attempts, int correctAttempts) {
        this(masteryScore, attempts, correctAttempts, null, 1.0);
    }

    public LearnerStateEvidence {
        masteryScore = bounded(masteryScore);
        attempts = Math.max(0, attempts);
        correctAttempts = Math.max(0, Math.min(attempts, correctAttempts));
        retentionScore = bounded(retentionScore);
    }

    /** 根据捕获时点校正后的掌握度；原始掌握度仍通过 masteryScore() 保留。 */
    public double effectiveMastery() {
        return LearnerRetentionModel.effectiveMastery(masteryScore, retentionScore);
    }

    public double forgettingRisk() {
        return LearnerRetentionModel.forgettingRisk(retentionScore);
    }

    public double confidenceLower() {
        return wilsonInterval()[0];
    }

    public double confidenceUpper() {
        return wilsonInterval()[1];
    }

    public double uncertainty() {
        double[] interval = wilsonInterval();
        return Math.max(0.0, interval[1] - interval[0]);
    }

    /** 在证据不足时采用较低掌握度，优先暴露可能遗漏的前置知识。 */
    public double conservativeMastery() {
        return confidenceLower();
    }

    private double[] wilsonInterval() {
        if (attempts <= 0) return new double[]{0.0, 1.0};
        double n = attempts;
        double z = 1.96;
        double p = effectiveMastery();
        double denominator = 1.0 + z * z / n;
        double center = (p + z * z / (2.0 * n)) / denominator;
        double margin = z * Math.sqrt((p * (1.0 - p) / n) + (z * z / (4.0 * n * n)))
                / denominator;
        return new double[]{bounded(center - margin), bounded(center + margin)};
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
