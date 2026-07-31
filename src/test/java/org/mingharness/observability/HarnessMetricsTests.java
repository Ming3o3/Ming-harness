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
}
