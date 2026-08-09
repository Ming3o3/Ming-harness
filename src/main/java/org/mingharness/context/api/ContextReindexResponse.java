package org.mingharness.context.api;

/** 一次有界索引重建的结果摘要，不包含供应商异常正文或上下文数据。 */
public record ContextReindexResponse(
        String scope,
        boolean embeddingReady,
        int parentsScanned,
        int parentsRebuilt,
        int chunksCreated,
        int chunksIndexed,
        int chunksFailed,
        int pendingChunks
) {
}
