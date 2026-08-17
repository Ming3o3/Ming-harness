package org.mingharness.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingPropertiesTests {

    @Test
    void shouldCapConfiguredBatchSizeAtProviderSafeLimit() {
        EmbeddingProperties properties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                1536, 20, 1_000, 100_000, 1, 0, 10_000);

        assertEquals(20, properties.batchSize());
        assertEquals(10, properties.effectiveBatchSize());
    }

    @Test
    void shouldKeepConfiguredBatchSizeWhenItIsWithinProviderLimit() {
        EmbeddingProperties properties = new EmbeddingProperties(true, "http://embedding", "key", "model",
                1536, 7, 1_000, 100_000, 1, 0, 10_000);

        assertEquals(7, properties.effectiveBatchSize());
    }
}
