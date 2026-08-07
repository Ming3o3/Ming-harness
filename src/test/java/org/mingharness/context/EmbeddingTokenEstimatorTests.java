package org.mingharness.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingTokenEstimatorTests {

    @Test
    void shouldConservativelyCountMixedLanguageInput() {
        assertEquals(3, EmbeddingTokenEstimator.estimate("中文A"));
        assertTrue(EmbeddingTokenEstimator.estimate("hello world") >= 4);
    }

    @Test
    void shouldTruncateByCodePointWithoutExceedingBudget() {
        String value = "中文🙂abcdef";

        String truncated = EmbeddingTokenEstimator.truncate(value, 3);

        assertEquals("中文🙂", truncated);
        assertTrue(EmbeddingTokenEstimator.estimate(truncated) <= 3);
        assertEquals(value, EmbeddingTokenEstimator.truncate(value, 20));
    }
}
