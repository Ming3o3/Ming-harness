package org.mingharness.context;

/** 分块器输出的尚未持久化的子块。 */
public record ContextChunkDraft(int chunkIndex, String content) {
}
