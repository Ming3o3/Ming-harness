package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** 向量召回、父文档上下文扩展和混合排序的有界参数。 */
@ConfigurationProperties(prefix = "harness.context.retrieval")
public record ContextRetrievalProperties(
        int candidateLimit,
        int maxParents,
        int neighborRadius,
        double minSimilarity,
        boolean rrfEnabled,
        int rrfK,
        double vectorWeight,
        double keywordWeight,
        int candidateExpansionFactor,
        int maxCandidatesPerParent
) {

    /** 保持已有测试和本地构造调用的默认混合排序行为。 */
    public ContextRetrievalProperties(int candidateLimit, int maxParents, int neighborRadius,
                                      double minSimilarity) {
        this(candidateLimit, maxParents, neighborRadius, minSimilarity,
                true, 60, 1.0, 0.7, 3, 4);
    }

    /** 保持显式 RRF 参数调用的兼容性；候选池默认扩展三倍。 */
    public ContextRetrievalProperties(int candidateLimit, int maxParents, int neighborRadius,
                                      double minSimilarity, boolean rrfEnabled, int rrfK,
                                      double vectorWeight, double keywordWeight) {
        this(candidateLimit, maxParents, neighborRadius, minSimilarity, rrfEnabled, rrfK,
                vectorWeight, keywordWeight, 3, 4);
    }

    @ConstructorBinding
    public ContextRetrievalProperties {
        candidateLimit = Math.min(200, Math.max(1, candidateLimit));
        maxParents = Math.min(50, Math.max(1, maxParents));
        neighborRadius = Math.min(5, Math.max(0, neighborRadius));
        minSimilarity = Double.isFinite(minSimilarity) ? Math.min(1.0, Math.max(-1.0, minSimilarity)) : 0.7;
        rrfK = Math.min(1_000, Math.max(1, rrfK));
        vectorWeight = finiteNonNegative(vectorWeight, 1.0);
        keywordWeight = finiteNonNegative(keywordWeight, 0.7);
        candidateExpansionFactor = Math.min(10, Math.max(1, candidateExpansionFactor));
        maxCandidatesPerParent = Math.min(50, Math.max(1, maxCandidatesPerParent));
        if (vectorWeight == 0.0 && keywordWeight == 0.0) {
            vectorWeight = 1.0;
        }
    }

    /** 返回用于 pgvector 查询的候选子块数量，防止单个父窗口垄断最终父来源。 */
    public int expandedCandidateLimit() {
        long expanded = (long) candidateLimit * candidateExpansionFactor;
        return (int) Math.min(1_000L, Math.max(candidateLimit, expanded));
    }

    /**
     * 为父来源多样性准备更大的数据库候选池，之后再按父来源上限裁剪。
     *
     * <p>候选池至少覆盖“目标父来源数 × 每个父来源允许的子块数”，再叠加
     * 查询扩展倍数，避免某个父来源的头部子块在窗口裁剪前就把其他父来源挤出池子。</p>
     */
    public int candidatePoolLimit() {
        long pool = (long) expandedCandidateLimit() * maxParents * maxCandidatesPerParent;
        return (int) Math.min(2_000L, Math.max(expandedCandidateLimit(), pool));
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(10.0, value)) : fallback;
    }
}
