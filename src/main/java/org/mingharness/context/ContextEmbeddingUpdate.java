package org.mingharness.context;

/** 一个 chunk 的向量写入请求。 */
public record ContextEmbeddingUpdate(ContextChunk chunk, EmbeddingVector vector) {
}
