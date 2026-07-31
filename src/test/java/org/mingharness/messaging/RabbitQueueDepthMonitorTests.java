package org.mingharness.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mingharness.config.MessagingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证队列深度读取和 RabbitMQ 故障时的安全失败行为。 */
class RabbitQueueDepthMonitorTests {

    @Test
    void shouldCalculateRemainingCapacityAndPublishGauges() {
        AmqpAdmin admin = mock(AmqpAdmin.class);
        MessagingProperties properties = mock(MessagingProperties.class);
        when(properties.queue()).thenReturn("harness.run.execute");
        when(properties.maxQueueDepth()).thenReturn(10);
        when(admin.getQueueInfo("harness.run.execute"))
                .thenReturn(new QueueInformation("harness.run.execute", 7, 1));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitQueueDepthMonitor monitor = new RabbitQueueDepthMonitor(
                admin, properties, new HarnessMetrics(registry));

        OptionalInt capacity = monitor.availableCapacity();

        assertTrue(capacity.isPresent());
        assertEquals(3, capacity.getAsInt());
        assertEquals(7.0, registry.get("harness.rabbit.queue.depth").gauge().value());
        assertEquals(3.0, registry.get("harness.rabbit.queue.capacity").gauge().value());
    }

    @Test
    void shouldFailClosedWhenQueueDepthCannotBeRead() {
        AmqpAdmin admin = mock(AmqpAdmin.class);
        MessagingProperties properties = mock(MessagingProperties.class);
        when(properties.queue()).thenReturn("harness.run.execute");
        when(admin.getQueueInfo("harness.run.execute")).thenThrow(new IllegalStateException("RabbitMQ 不可用"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitQueueDepthMonitor monitor = new RabbitQueueDepthMonitor(
                admin, properties, new HarnessMetrics(registry));

        assertTrue(monitor.availableCapacity().isEmpty());
        assertEquals(1.0, registry.get("harness.rabbit.queue.poll_failures").counter().count());
        assertEquals(-1.0, registry.get("harness.rabbit.queue.depth").gauge().value());
    }
}
