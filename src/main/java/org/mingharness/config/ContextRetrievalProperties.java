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
        double keywordWeight
) {

    /** 保持已有测试和本地构造调用的默认混合排序行为。 */
    public ContextRetrievalProperties(int candidateLimit, int maxParents, int neighborRadius,
                                      double minSimilarity) {
        this(candidateLimit, maxParents, neighborRadius, minSimilarity,
                true, 60, 1.0, 0.7);
    }

    @ConstructorBinding
    public ContextRetrievalProperties {
        candidateLimit = Math.min(200, Math.max(1, candidateLimit));
        maxParents = Math.min(50, Math.max(1, maxParents));
        neighborRadius = Math.min(5, Math.max(0, neighborRadius));
        minSimilarity = Double.isFinite(minSimilarity) ? Math.min(1.0, Math.max(-1.0, minSimilarity)) : 0.2;
        rrfK = Math.min(1_000, Math.max(1, rrfK));
        vectorWeight = finiteNonNegative(vectorWeight, 1.0);
        keywordWeight = finiteNonNegative(keywordWeight, 0.7);
        if (vectorWeight == 0.0 && keywordWeight == 0.0) {
            vectorWeight = 1.0;
        }
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(10.0, value)) : fallback;
    }
}
