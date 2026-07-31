package org.mingharness.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.config.MessagingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.mingharness.runtime.application.RunExecutionStateService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证最终 Outbox 失败不会让对应 Run 长时间停留在 RUNNING。 */
class OutboxRelayFailureTests {

    @Test
    void shouldFailRunImmediatelyWhenOutboxExhaustsItsPublishAttempts() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessagingProperties properties = mock(MessagingProperties.class);
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        RabbitQueueDepthMonitor monitor = mock(RabbitQueueDepthMonitor.class);
        RunExecutionStateService executionStateService = mock(RunExecutionStateService.class);
        when(monitor.availableCapacity()).thenReturn(OptionalInt.of(1));
        when(claimService.claimPending(1)).thenReturn(List.of(new OutboxClaim("event-1", "not-json")));
        when(claimService.markFailed(eq("event-1"), anyString())).thenReturn(true);
        when(claimService.findTerminalFailure("event-1")).thenReturn(Optional.of(
                new OutboxClaimService.FailedOutbox("run-1", "tenant-1", "消息格式无效")));
        when(executionStateService.failAfterDispatchFailure("run-1", "tenant-1", "消息格式无效"))
                .thenReturn(true);

        OutboxRelay relay = new OutboxRelay(rabbitTemplate, new ObjectMapper(), properties,
                claimService, monitor, new HarnessMetrics(new SimpleMeterRegistry()), executionStateService);

        relay.publishPending();

        verify(executionStateService).failAfterDispatchFailure("run-1", "tenant-1", "消息格式无效");
    }
}
