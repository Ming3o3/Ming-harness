package org.mingharness.education;

import java.time.Duration;
import java.time.Instant;

/**
 * 学习者状态的确定性保持度模型。
 *
 * <p>保持度只作为掌握度的时间校正，不会凭空制造一次新的答题证据。模型参数和版本
 * 固定在代码中，并在 Run 状态快照中记录捕获时点，因此历史 Run 重放不会因为当前时间
 * 变化而得到不同的检索结果。</p>
 */
public final class LearnerRetentionModel {

    public static final String VERSION = "EXPONENTIAL_HALF_LIFE_V1";
    public static final Duration DEFAULT_HALF_LIFE = Duration.ofDays(14);
    private static final double PRIOR_MASTERY = 0.5;

    private LearnerRetentionModel() {
    }

    /** 返回 [0,1] 的保持度；没有有效时间证据时保持中性值 1。 */
    public static double retentionAt(Instant lastAssessedAt, Instant capturedAt) {
        return retentionAt(lastAssessedAt, capturedAt, DEFAULT_HALF_LIFE);
    }

    public static double retentionAt(Instant lastAssessedAt, Instant capturedAt,
                                     Duration halfLife) {
        if (lastAssessedAt == null || capturedAt == null
                || !capturedAt.isAfter(lastAssessedAt)) {
            return 1.0;
        }
        Duration effectiveHalfLife = halfLife == null || halfLife.isZero() || halfLife.isNegative()
                ? DEFAULT_HALF_LIFE : halfLife;
        double elapsedSeconds = Math.max(0.0,
                Duration.between(lastAssessedAt, capturedAt).toNanos() / 1_000_000_000.0);
        // Run 创建会在读取实体后立即生成快照；亚秒级的调度抖动不应被解释为遗忘。
        if (elapsedSeconds <= 1.0) return 1.0;
        double halfLifeSeconds = Math.max(1.0,
                effectiveHalfLife.toNanos() / 1_000_000_000.0);
        double retention = Math.exp(-Math.log(2.0) * elapsedSeconds / halfLifeSeconds);
        return bounded(retention);
    }

    /**
     * 将原始掌握度向中性先验 0.5 收缩，表示长期未复习后的保守状态。
     * 这样遗忘风险不会把低掌握度错误地解释为新的错误证据。
     */
    public static double effectiveMastery(double masteryScore, double retentionScore) {
        double mastery = bounded(masteryScore);
        double retention = bounded(retentionScore);
        return bounded(PRIOR_MASTERY + (mastery - PRIOR_MASTERY) * retention);
    }

    public static double forgettingRisk(double retentionScore) {
        return bounded(1.0 - bounded(retentionScore));
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
