package org.mingharness.education;

/**
 * 一个可解释、可测试的简化 BKT 更新器。
 *
 * <p>题目难度不会直接把分数“加分”，而是改变猜对概率和失误概率：答对一道难题
 * 的证据更强，答错一道难题也不会被当作完全不会。提示和非独立作答会降低这次
 * 证据的权重，避免一次偶然表现把掌握度推得过高或过低。</p>
 */
public final class BktMasteryCalculator {

    public static final double DEFAULT_GUESS = 0.20;
    public static final double DEFAULT_SLIP = 0.10;
    public static final double DEFAULT_LEARN = 0.15;

    private BktMasteryCalculator() {
    }

    public static Result update(double prior, boolean correct, int difficultyLevel,
                                double evidenceWeight, boolean hintUsed) {
        double p = clamp(prior);
        int difficulty = Math.max(1, Math.min(5, difficultyLevel));
        double weight = clamp(evidenceWeight);

        // 难度越高，猜对概率越低；同时高难题更容易出现失误。
        double guess = clamp(DEFAULT_GUESS - 0.025 * (difficulty - 3), 0.08, 0.30);
        double slip = clamp(DEFAULT_SLIP + 0.025 * (difficulty - 3), 0.08, 0.22);
        double posterior;
        if (correct) {
            double numerator = p * (1.0 - slip);
            double denominator = numerator + (1.0 - p) * guess;
            posterior = denominator <= 0.0 ? p : numerator / denominator;
        } else {
            double numerator = p * slip;
            double denominator = numerator + (1.0 - p) * (1.0 - guess);
            posterior = denominator <= 0.0 ? p : numerator / denominator;
        }

        // 提示或非满权重证据只让本次后验向先验靠近，不篡改历史数据。
        double effectivePosterior = p + (posterior - p) * weight;
        double learn = clamp(DEFAULT_LEARN + 0.02 * (difficulty - 3), 0.08, 0.22);
        if (hintUsed) learn *= 0.65;
        double next = effectivePosterior + (1.0 - effectivePosterior) * learn * weight;
        return new Result(clamp(next), clamp(posterior), guess, slip, learn, weight);
    }

    public static Result update(double prior, boolean correct, int difficultyLevel) {
        return update(prior, correct, difficultyLevel, 1.0, false);
    }

    private static double clamp(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    public record Result(double nextMastery, double posterior, double guessProbability,
                         double slipProbability, double learningProbability,
                         double evidenceWeight) {
    }
}
