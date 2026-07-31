package org.mingharness.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.MessagingProperties;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 验证多实例 Outbox Relay 的租约抢占和所有权校验。 */
@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTests {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private MessagingProperties properties;

    @Test
    void shouldAllowOnlyOneRelayToClaimActiveEvent() {
        OutboxEvent event = new OutboxEvent(
                "run-claim", "tenant-claim", "trace-claim", "START", "{}");
        when(properties.outboxClaimLeaseMs()).thenReturn(30_000L);
        when(repository.findClaimableForUpdate(eq(OutboxStatus.PENDING), eq(OutboxStatus.PUBLISHING),
                any(Instant.class), any())).thenReturn(List.of(event), List.of());

        OutboxClaimService firstRelay = new OutboxClaimService(repository, properties, new SensitiveDataSanitizer());
        OutboxClaimService secondRelay = new OutboxClaimService(repository, properties, new SensitiveDataSanitizer());

        assertEquals(1, firstRelay.claimPending().size());
        assertTrue(secondRelay.claimPending().isEmpty());
        when(repository.findByIdForPublishUpdate(event.getId())).thenReturn(Optional.of(event));
        assertFalse(secondRelay.markPublished(event.getId()));
        assertTrue(firstRelay.markPublished(event.getId()));
    }

    @Test
    void shouldReclaimExpiredLeaseButRejectStaleFinalization() {
        OutboxEvent event = new OutboxEvent(
                "run-expire", "tenant-claim", "trace-expire", "START", "{}");
        Instant claimAt = Instant.now().minusSeconds(30);
        event.claim("old-relay", claimAt, Instant.now().minusSeconds(1));
        when(properties.outboxClaimLeaseMs()).thenReturn(30_000L);
        when(repository.findClaimableForUpdate(eq(OutboxStatus.PENDING), eq(OutboxStatus.PUBLISHING),
                any(Instant.class), any())).thenReturn(List.of(event));
        when(repository.findByIdForPublishUpdate(event.getId())).thenReturn(Optional.of(event));

        OutboxClaimService newRelay = new OutboxClaimService(repository, properties, new SensitiveDataSanitizer());
        assertEquals(1, newRelay.claimPending().size());
        assertFalse(event.markPublished("old-relay"));
        assertTrue(newRelay.markPublished(event.getId()));
    }
}
