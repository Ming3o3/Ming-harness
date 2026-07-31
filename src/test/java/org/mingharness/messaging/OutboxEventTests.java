package org.mingharness.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxEventTests {

    @Test
    void shouldRetryThenMarkEventFailedAfterMaximumAttempts() {
        OutboxEvent event = new OutboxEvent("run-1", "tenant-1", "trace-1", "START", "{}");
        Instant now = Instant.now();

        assertTrue(event.claim("relay-a", now, now.plusSeconds(30)));
        assertTrue(event.markFailed("relay-a", "第一次失败", 3));
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());

        Instant retryAt = event.getNextAttemptAt().plusMillis(1);
        assertTrue(event.claim("relay-a", retryAt, retryAt.plusSeconds(30)));
        assertTrue(event.markFailed("relay-a", "第二次失败", 3));
        Instant finalRetryAt = event.getNextAttemptAt().plusMillis(1);
        assertTrue(event.claim("relay-a", finalRetryAt, finalRetryAt.plusSeconds(30)));
        assertTrue(event.markFailed("relay-a", "第三次失败", 3));
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(3, event.getAttempts());
    }

    @Test
    void shouldClearErrorWhenPublished() {
        OutboxEvent event = new OutboxEvent("run-2", "tenant-1", "trace-2", "RETRY", "{}");
        Instant now = Instant.now();
        event.claim("relay-a", now, now.plusSeconds(30));
        event.markFailed("relay-a", "暂时失败", 3);
        Instant retryAt = event.getNextAttemptAt().plusMillis(1);
        event.claim("relay-a", retryAt, retryAt.plusSeconds(30));
        event.markPublished("relay-a");

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(null, event.getLastError());
    }

    @Test
    void shouldPreventAnotherRelayFromFinalizingAnActiveClaim() {
        OutboxEvent event = new OutboxEvent("run-3", "tenant-1", "trace-3", "START", "{}");
        Instant now = Instant.now();

        assertTrue(event.claim("relay-a", now, now.plusSeconds(30)));
        assertFalse(event.markPublished("relay-b"));
        assertEquals(OutboxStatus.PUBLISHING, event.getStatus());

        Instant expired = now.plusSeconds(31);
        assertTrue(event.claim("relay-b", expired, expired.plusSeconds(30)));
        assertFalse(event.markFailed("relay-a", "过期 Relay 不得覆盖", 3));
        assertTrue(event.markPublished("relay-b"));
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
    }
}
