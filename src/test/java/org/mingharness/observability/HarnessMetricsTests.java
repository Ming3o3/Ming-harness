package org.mingharness.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证健康摘要只暴露低基数运行指标，并正确隐藏未知队列哨兵值。 */
class HarnessMetricsTests {

    @Test
    void shouldExposeWorkerQueueAndMessageGovernanceSnapshot() {
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        metrics.workerConcurrency(4);
        metrics.workerStarted();
        metrics.workerStarted();
        metrics.rabbitQueueDepth(7);
        metrics.rabbitQueueCapacity(993);
        metrics.pendingOutbox(3);
        metrics.rabbitRetry();
        metrics.rabbitDeadLetter();
        metrics.runTimedOut();

        HarnessMetrics.OperationalSnapshot snapshot = metrics.operationalSnapshot();

        assertEquals(2, snapshot.activeWorkers());
        assertEquals(4, snapshot.workerConcurrencyLimit());
        assertEquals(7, snapshot.queueDepth());
        assertEquals(993, snapshot.queueCapacity());
        assertEquals(3, snapshot.pendingOutbox());
        assertEquals(1, snapshot.rabbitRetryCount());
        assertEquals(1, snapshot.rabbitDeadLetterCount());
        assertEquals(1, snapshot.timedOutRunCount());
    }

    @Test
    void shouldHideUnknownQueueMetrics() {
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());

        HarnessMetrics.OperationalSnapshot snapshot = metrics.operationalSnapshot();

        assertNull(snapshot.queueDepth());
        assertNull(snapshot.queueCapacity());
    }

    @Test
    void shouldRecordContextRetrievalAndIndexSignals() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HarnessMetrics metrics = new HarnessMetrics(registry);

        metrics.contextEmbeddingRequest();
        metrics.contextEmbeddingRetry();
        metrics.contextEmbeddingFailed();
        metrics.contextEmbeddingCacheHit();
        metrics.contextEmbeddingCacheMiss();
        metrics.contextQueryEmbeddingCacheHit();
        metrics.contextQueryEmbeddingCacheMiss();
        metrics.contextVectorQuery();
        metrics.contextVectorHits(3);
        metrics.contextKeywordSupplements(2);
        metrics.contextFallback();
        metrics.contextChunksIndexed(4);
        metrics.contextIndexFailure();
        metrics.contextIndexPending(7);
        metrics.recordContextRetrieval(() -> "ok");

        assertEquals(1.0, registry.get("harness.context.embedding.requests").counter().count());
        assertEquals(1.0, registry.get("harness.context.embedding.retries").counter().count());
        assertEquals(1.0, registry.get("harness.context.embedding.failed").counter().count());
        assertEquals(1.0, registry.get("harness.context.embedding.cache.hits").counter().count());
        assertEquals(1.0, registry.get("harness.context.embedding.cache.misses").counter().count());
        assertEquals(1.0, registry.get("harness.context.query_embedding.cache.hits").counter().count());
        assertEquals(1.0, registry.get("harness.context.query_embedding.cache.misses").counter().count());
        assertEquals(1.0, registry.get("harness.context.vector.queries").counter().count());
        assertEquals(3.0, registry.get("harness.context.vector.hits").counter().count());
        assertEquals(2.0, registry.get("harness.context.keyword.supplements").counter().count());
        assertEquals(1.0, registry.get("harness.context.retrieval.fallbacks").counter().count());
        assertEquals(4.0, registry.get("harness.context.index.chunks").counter().count());
        assertEquals(1.0, registry.get("harness.context.index.failures").counter().count());
        assertEquals(7.0, registry.get("harness.context.index.pending").gauge().value());
        assertEquals(1, registry.get("harness.context.retrieval.duration").timer().count());
    }
}
