package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 向量召回和父文档上下文扩展的有界参数。 */
@ConfigurationProperties(prefix = "harness.context.retrieval")
public record ContextRetrievalProperties(
        int candidateLimit,
        int maxParents,
        int neighborRadius,
        double minSimilarity
) {

    public ContextRetrievalProperties {
        candidateLimit = Math.min(200, Math.max(1, candidateLimit));
        maxParents = Math.min(50, Math.max(1, maxParents));
        neighborRadius = Math.min(5, Math.max(0, neighborRadius));
        minSimilarity = Double.isFinite(minSimilarity) ? Math.min(1.0, Math.max(-1.0, minSimilarity)) : 0.2;
    }
}
