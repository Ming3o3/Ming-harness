package org.mingharness.context;

import org.junit.jupiter.api.Test;
import org.mingharness.config.ContextChunkingProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextChunkerTests {

    private final ContextChunker chunker = new ContextChunker(new ContextChunkingProperties(128, 16));

    @Test
    void shouldKeepParagraphsTogetherUntilConfiguredBoundary() {
        var chunks = chunker.chunk("第一段内容。\n\n第二段内容。\n\n第三段内容。");

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).content().contains("第一段内容"));
        assertTrue(chunks.get(0).content().contains("第三段内容"));
    }

    @Test
    void shouldSplitLongUnitNearPunctuationAndKeepOverlap() {
        var chunks = chunker.chunk(("甲甲甲甲甲甲甲甲甲甲甲甲甲甲甲。乙乙乙乙乙乙乙乙乙乙乙乙乙。丙丙丙丙丙丙丙丙丙丙丙丙丙丙丙。\n\n").repeat(8));

        assertTrue(chunks.size() > 1);
        assertEquals(0, chunks.get(0).chunkIndex());
        assertEquals(1, chunks.get(1).chunkIndex());
        assertTrue(chunks.get(0).content().substring(chunks.get(0).content().length() - 16)
                .equals(chunks.get(1).content().substring(0, 16)));
    }
}
