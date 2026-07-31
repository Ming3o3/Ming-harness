package org.mingharness.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.config.MessagingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.OptionalInt;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Outbox 在队列达到上限或状态未知时不会继续抢占消息。 */
class OutboxRelayBackpressureTests {

    @Test
    void shouldPauseWhenQueueIsFull() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessagingProperties properties = mock(MessagingProperties.class);
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        RabbitQueueDepthMonitor monitor = mock(RabbitQueueDepthMonitor.class);
        when(monitor.availableCapacity()).thenReturn(OptionalInt.of(0));
        HarnessMetrics metrics = new HarnessMetrics(new SimpleMeterRegistry());
        OutboxRelay relay = new OutboxRelay(rabbitTemplate, new ObjectMapper(), properties,
                claimService, monitor, metrics);

        relay.publishPending();

        verify(claimService, never()).claimPending(anyInt());
    }

    @Test
    void shouldLimitClaimBatchToObservedCapacity() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessagingProperties properties = mock(MessagingProperties.class);
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        RabbitQueueDepthMonitor monitor = mock(RabbitQueueDepthMonitor.class);
        when(monitor.availableCapacity()).thenReturn(OptionalInt.of(3));
        when(claimService.claimPending(3)).thenReturn(java.util.List.of());
        OutboxRelay relay = new OutboxRelay(rabbitTemplate, new ObjectMapper(), properties,
                claimService, monitor, new HarnessMetrics(new SimpleMeterRegistry()));

        relay.publishPending();

        verify(claimService).claimPending(3);
    }
}
