package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 教师证据标注聚合出的、可绑定到教育 Run 的检索权重快照。
 *
 * <p>快照同时保存量规均值和样本量，便于回放时判断权重来自多少人工事实；新一轮
 * 标注不会改写已经运行过的 Run。</p>
 */
public record EducationRetrievalCalibrationSnapshot(
        String version,
        long sampleCount,
        double targetGroundingMean,
        double prerequisiteUtilityMean,
        double difficultyFitMean,
        double overallUtilityMean,
        EducationRankingWeights weights,
        Map<String, EducationRetrievalCalibrationSlice> stateSlices
) {

    public static final String VERSION = "retrieval-calibration-v2";
    public static final long MIN_STATE_SLICE_SAMPLE_COUNT = 5;

    /** 兼容 v1 快照构造方式；没有状态分层时继续使用租户级权重。 */
    public EducationRetrievalCalibrationSnapshot(String version, long sampleCount,
                                                 double targetGroundingMean,
                                                 double prerequisiteUtilityMean,
                                                 double difficultyFitMean,
                                                 double overallUtilityMean,
                                                 EducationRankingWeights weights) {
        this(version, sampleCount, targetGroundingMean, prerequisiteUtilityMean,
                difficultyFitMean, overallUtilityMean, weights, Map.of());
    }

    public EducationRetrievalCalibrationSnapshot {
        version = version == null || version.isBlank() ? VERSION : version.trim();
        sampleCount = Math.max(0, sampleCount);
        targetGroundingMean = score(targetGroundingMean);
        prerequisiteUtilityMean = score(prerequisiteUtilityMean);
        difficultyFitMean = score(difficultyFitMean);
        overallUtilityMean = score(overallUtilityMean);
        weights = weights == null ? EducationRankingWeights.fixed() : weights;
        if (stateSlices == null || stateSlices.isEmpty()) {
            stateSlices = Map.of();
        } else {
            Map<String, EducationRetrievalCalibrationSlice> normalized = new LinkedHashMap<>();
            stateSlices.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    normalized.put(key.trim(), value);
                }
            });
            stateSlices = Map.copyOf(normalized);
        }
    }

    public static EducationRetrievalCalibrationSnapshot prior() {
        return new EducationRetrievalCalibrationSnapshot(
                VERSION, 0, 3.0, 3.0, 3.0, 3.0,
                new EducationRankingWeights(0.35, 0.20, 0.15, 0.15, 0.15,
                        "CALIBRATED_PRIOR"), Map.of());
    }

    public boolean hasEvidence() {
        return sampleCount > 0;
    }

    /** 返回对应学习状态分层权重；没有足够分层事实时回退租户级快照。 */
    public EducationRankingWeights weightsFor(String conditioning) {
        if (conditioning == null || conditioning.isBlank()) return weights;
        EducationRetrievalCalibrationSlice slice = stateSlices.get(conditioning.trim());
        return slice == null || slice.sampleCount() < MIN_STATE_SLICE_SAMPLE_COUNT
                ? weights : slice.weights();
    }

    private static double score(double value) {
        return Double.isFinite(value) ? Math.max(1.0, Math.min(5.0, value)) : 3.0;
    }
}
