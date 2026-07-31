package org.mingharness.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxEventTests {

    @Test
    void shouldRetryThenMarkEventFailedAfterMaximumAttempts() {
        OutboxEvent event = new OutboxEvent("run-1", "tenant-1", "trace-1", "START", "{}");

        event.markFailed("第一次失败", 3);
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());

        event.markFailed("第二次失败", 3);
        event.markFailed("第三次失败", 3);
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(3, event.getAttempts());
    }

    @Test
    void shouldClearErrorWhenPublished() {
        OutboxEvent event = new OutboxEvent("run-2", "tenant-1", "trace-2", "RETRY", "{}");
        event.markFailed("暂时失败", 3);
        event.markPublished();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(null, event.getLastError());
    }
}
