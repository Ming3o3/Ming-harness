package org.mingharness.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证 Rabbit Worker 的并发、预取和队列背压配置会被安全归一化。 */
class MessagingPropertiesTests {

    @Test
    void shouldApplySafeDefaultsAndKeepMaxConcurrencyAboveBaseConcurrency() {
        MessagingProperties properties = new MessagingProperties(
                true, null, null, null, null, null,
                0, 0, 0, 3, 1, 0, 0, 0);

        assertEquals(3, properties.consumerConcurrency());
        assertEquals(3, properties.maxConsumerConcurrency());
        assertEquals(1, properties.prefetch());
        assertEquals(1_000, properties.maxQueueDepth());
        assertEquals(5_000, properties.queueMetricsPollMs());
    }
}
